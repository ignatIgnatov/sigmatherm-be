package com.ludogoriesoft.sigmatherm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludogoriesoft.sigmatherm.dto.SkroutzOrderWebhook;
import com.ludogoriesoft.sigmatherm.model.Product;
import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.model.WebhookEventLog;
import com.ludogoriesoft.sigmatherm.model.enums.EventType;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.repository.WebhookEventLogRepository;
import com.ludogoriesoft.sigmatherm.service.ProductService;
import com.ludogoriesoft.sigmatherm.service.SkroutzFeedService;
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

@Slf4j
@RestController
@RequestMapping("/api/skroutz-orders")
@RequiredArgsConstructor
public class SkroutzWebhookController {

    private final ProductService productService;
    private final SkroutzFeedService skroutzFeedService;
    private final ObjectMapper objectMapper;
    private final SynchronizationService synchronizationService;
    private final WebhookEventLogRepository webhookEventLogRepository;

    private static final String FEED_PATH = "/app/feeds/skroutz_feed.xml";

    @Value("${skroutz.webhook.secret}")
    private String skroutzSecret;

    @PostMapping
    public ResponseEntity<String> receiveOrder(@RequestBody String rawPayload) {

        log.debug("Received raw webhook payload: {}", rawPayload);

        try {
            SkroutzOrderWebhook webhook = objectMapper.readValue(rawPayload, SkroutzOrderWebhook.class);
            EventType type = webhook.getEvent_type();
            String state = webhook.getOrder().getState();
            String orderCode = webhook.getOrder().getCode();

            if (type == null || orderCode == null) {
                log.error("Invalid or missing event_type or order_code");
                return ResponseEntity.badRequest().body("Invalid or missing event_type or order_code");
            }

            log.info("Received Skroutz webhook type: " + type + " with state: " + state);

            boolean alreadyHandled = webhookEventLogRepository.existsByOrderIdAndEventType(orderCode, type.name());
            if (alreadyHandled) {
                log.warn("Duplicate webhook received for order {} and type {}, skipping processing", orderCode, type);
                return ResponseEntity.ok("Duplicate webhook ignored");
            }

            WebhookEventLog eventLog = new WebhookEventLog();
            eventLog.setOrderId(orderCode);
            eventLog.setEventType(type.name());
            webhookEventLogRepository.save(eventLog);

            if (state.equals("accepted") || state.equals("returned")) {
                log.info("Received raw webhook payload: {}", rawPayload);
                Synchronization synchronization = synchronizationService.createSync(Platform.Skroutz);
                switch (state) {
                    case "accepted":
                        webhook.getOrder().getLine_items().forEach(line -> {
                            String productId = line.getId();
                            int quantity = line.getQuantity();
                            String name = line.getProduct_name();
                            log.info("Product with id: {} and name: {}", productId, name);
                            productService.reduceAvailabilityByOrder(productId, quantity);
                            productService.setSync(productId, synchronization);
                            updateSkroutzXml(productId);
                        });
                        break;
                    case "returned":
                        webhook.getOrder().getLine_items().forEach(line -> {
                            String productId = line.getId();
                            int quantity = line.getQuantity();
                            productService.increaseAvailabilityByReturn(productId, quantity);
                            productService.setSync(productId, synchronization);
                            updateSkroutzXml(productId);
                        });
                        break;
                    default:
                        return new ResponseEntity<>("Unsupported order state", HttpStatus.BAD_REQUEST);
                }
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Error processing Skroutz webhook: ", e);
            return ResponseEntity.status(500).body("Error processing Skroutz webhook: " + e);
        }
    }

    private void updateSkroutzXml(String productId) {
        Product product = productService.findProductById(productId);
        try {
            if (product != null) {
                skroutzFeedService.processStockUpdateToSkroutz(new File(FEED_PATH), List.of(product));
            }
        } catch (Exception e) {
            log.error("Update feed exception: " + e.getMessage());
        }
    }
}
