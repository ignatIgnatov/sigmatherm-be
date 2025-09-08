package com.ludogoriesoft.sigmatherm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludogoriesoft.sigmatherm.dto.SkroutzOrderWebhook;
import com.ludogoriesoft.sigmatherm.model.Product;
import com.ludogoriesoft.sigmatherm.model.SyncLog;
import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.model.WebhookEventLog;
import com.ludogoriesoft.sigmatherm.model.enums.EventType;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.model.enums.SyncDirection;
import com.ludogoriesoft.sigmatherm.model.enums.SyncOperation;
import com.ludogoriesoft.sigmatherm.repository.WebhookEventLogRepository;
import com.ludogoriesoft.sigmatherm.service.ProductService;
import com.ludogoriesoft.sigmatherm.service.SkroutzFeedService;
import com.ludogoriesoft.sigmatherm.service.SyncLogService;
import com.ludogoriesoft.sigmatherm.service.SynchronizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/skroutz-orders")
@RequiredArgsConstructor
public class SkroutzWebhookController {

    private final ProductService productService;
    private final SkroutzFeedService skroutzFeedService;
    private final ObjectMapper objectMapper;
    private final SynchronizationService synchronizationService;
    private final SyncLogService syncLogService;
    private final WebhookEventLogRepository webhookEventLogRepository;

    private static final String FEED_PATH = "/app/feeds/skroutz_feed.xml";

    @Value("${skroutz.webhook.secret}")
    private String skroutzSecret;

    @PostMapping
    public ResponseEntity<String> receiveOrder(@RequestBody String rawPayload) {
        log.debug("Received raw webhook payload: {}", rawPayload);

        // Generate unique batch ID for this webhook processing
        String batchId = "skroutz-webhook-" + System.currentTimeMillis();
        SyncLog webhookLog = null;

        try {
            SkroutzOrderWebhook webhook = objectMapper.readValue(rawPayload, SkroutzOrderWebhook.class);
            EventType type = webhook.getEvent_type();
            String state = webhook.getOrder().getState();
            String orderCode = webhook.getOrder().getCode();

            if (type == null || orderCode == null) {
                log.error("Invalid or missing event_type or order_code");
                return ResponseEntity.badRequest().body("Invalid or missing event_type or order_code");
            }

            log.info("Received Skroutz webhook type: {} with state: {} for order: {}", type, state, orderCode);

            // Check for duplicate webhooks
            boolean alreadyHandled = webhookEventLogRepository.existsByOrderIdAndEventType(orderCode, type.name());
            if (alreadyHandled) {
                log.warn("Duplicate webhook received for order {} and type {}, skipping processing", orderCode, type);

                // Log the duplicate webhook attempt
                syncLogService.logSingleOperation(
                        Platform.Skroutz,
                        SyncDirection.INBOUND,
                        SyncOperation.ORDERS,
                        null,
                        false,
                        String.format("Duplicate webhook for order %s, type %s", orderCode, type),
                        "Duplicate webhook ignored"
                );

                return ResponseEntity.ok("Duplicate webhook ignored");
            }

            // Process only accepted or returned orders
            if (!state.equals("accepted") && !state.equals("returned")) {
                log.info("Skipping webhook processing for unsupported state: {}", state);

                // Log the skipped webhook
                syncLogService.logSingleOperation(
                        Platform.Skroutz,
                        SyncDirection.INBOUND,
                        SyncOperation.ORDERS,
                        null,
                        true,
                        String.format("Skipped webhook for order %s with unsupported state %s", orderCode, state),
                        null
                );

                return ResponseEntity.ok("Order state not processed");
            }

            // Save webhook event log to prevent duplicates
            WebhookEventLog eventLog = new WebhookEventLog();
            eventLog.setOrderId(orderCode);
            eventLog.setEventType(type.name());
            webhookEventLogRepository.save(eventLog);

            // Start logging the webhook processing
            Synchronization synchronization = synchronizationService.createSync(Platform.Skroutz);
            SyncOperation operation = state.equals("accepted") ? SyncOperation.ORDERS : SyncOperation.RETURNS;

            webhookLog = syncLogService.startSync(
                    Platform.Skroutz,
                    SyncDirection.INBOUND,
                    operation,
                    synchronization,
                    batchId
            );

            // Process line items
            List<SkroutzOrderWebhook.LineItem> lineItems = webhook.getOrder().getLine_items();
            int totalItems = lineItems.size();
            int processedItems = 0;
            int successfulItems = 0;
            int failedItems = 0;

            log.info("Processing {} line items for {} order {}", totalItems, state, orderCode);

            for (SkroutzOrderWebhook.LineItem line : lineItems) {
                String productId = line.getId();
                int quantity = line.getQuantity();
                String productName = line.getProduct_name();

                try {
                    log.info("Processing product: {} ({}), quantity: {}", productName, productId, quantity);

                    if (state.equals("accepted")) {
                        productService.reduceAvailabilityByOrder(productId, quantity);
                    } else { // returned
                        productService.increaseAvailabilityByReturn(productId, quantity);
                    }

                    productService.setSync(productId, synchronization);

                    // Update Skroutz XML feed for this product
                    updateSkroutzXmlWithLogging(productId, batchId);

                    successfulItems++;
                    log.info("Successfully processed product {}", productId);

                } catch (Exception e) {
                    failedItems++;
                    log.error("Failed to process line item for product {}: {}", productId, e.getMessage(), e);
                }

                processedItems++;

                // Update progress in the log
                syncLogService.updateProgress(
                        webhookLog.getId(),
                        processedItems,
                        successfulItems,
                        failedItems,
                        String.format("Processed %d/%d items for %s order %s",
                                processedItems, totalItems, state, orderCode)
                );
            }

            // Complete the sync log
            String completionDetails = String.format(
                    "Webhook processed: Order %s (%s), Items: %d total, %d successful, %d failed",
                    orderCode, state, totalItems, successfulItems, failedItems
            );

            syncLogService.completeSync(webhookLog.getId(), totalItems, successfulItems, failedItems, completionDetails);

            if (failedItems > 0) {
                log.warn("Webhook processing completed with {} failures out of {} items", failedItems, totalItems);
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .body(String.format("Processed with %d failures", failedItems));
            } else {
                log.info("Webhook processing completed successfully for all {} items", totalItems);
                return ResponseEntity.ok("OK");
            }

        } catch (Exception e) {
            log.error("Error processing Skroutz webhook: ", e);

            // Fail the sync log if it was started
            if (webhookLog != null) {
                syncLogService.failSync(webhookLog.getId(), e.getMessage(), 0, 0, 0);
            } else {
                // Log the parsing/validation failure
                syncLogService.logSingleOperation(
                        Platform.Skroutz,
                        SyncDirection.INBOUND,
                        SyncOperation.ORDERS,
                        null,
                        false,
                        "Failed to parse webhook payload",
                        e.getMessage()
                );
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing Skroutz webhook: " + e.getMessage());
        }
    }

    private void updateSkroutzXmlWithLogging(String productId, String parentBatchId) {
        Product product = productService.findProductById(productId);

        if (product == null) {
            log.warn("Product {} not found for feed update", productId);

            // Log the failed feed update
            syncLogService.logSingleOperation(
                    Platform.Skroutz,
                    SyncDirection.OUTBOUND,
                    SyncOperation.FEED_UPDATE,
                    null,
                    false,
                    String.format("Feed update attempted for product %s", productId),
                    "Product not found"
            );
            return;
        }

        try {
            skroutzFeedService.processStockUpdateToSkroutz(new File(FEED_PATH), List.of(product));

            // Log successful feed update
            syncLogService.logSingleOperation(
                    Platform.Skroutz,
                    SyncDirection.OUTBOUND,
                    SyncOperation.FEED_UPDATE,
                    null,
                    true,
                    String.format("Feed updated for product %s (stock: %d)", productId, product.getStock()),
                    null
            );

            log.info("Successfully updated Skroutz feed for product {}", productId);

        } catch (Exception e) {
            log.error("Failed to update Skroutz feed for product {}: {}", productId, e.getMessage(), e);

            // Log failed feed update
            syncLogService.logSingleOperation(
                    Platform.Skroutz,
                    SyncDirection.OUTBOUND,
                    SyncOperation.FEED_UPDATE,
                    null,
                    false,
                    String.format("Feed update failed for product %s", productId),
                    e.getMessage()
            );
        }
    }
}