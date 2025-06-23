package com.ludogoriesoft.sigmatherm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludogoriesoft.sigmatherm.dto.SkroutzOrderWebhook;
import com.ludogoriesoft.sigmatherm.entity.Product;
import com.ludogoriesoft.sigmatherm.entity.Synchronization;
import com.ludogoriesoft.sigmatherm.entity.enums.EventType;
import com.ludogoriesoft.sigmatherm.entity.enums.Platform;
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

            if (type == null) {
                return ResponseEntity.badRequest().body("Invalid or missing event_type");
            }

            log.info("Received Skroutz webhook type: " + type + " with state: " + state);

            Synchronization synchronization = synchronizationService.createSync(Platform.Skroutz);

            switch (state) {
                case "accepted":
                    webhook.getOrder().getLine_items().forEach(line -> {
                        String productId = line.getId();
                        int quantity = line.getQuantity();
                        productService.setProductSynchronization(productId, synchronization);
                        productService.reduceAvailabilityByOrder(productId, quantity);
                        updateSkroutzXml(productId);
                    });
                    break;
                case "returned":
                    webhook.getOrder().getLine_items().forEach(line -> {
                        String productId = line.getId();
                        int quantity = line.getQuantity();
                        productService.setProductSynchronization(productId, synchronization);
                        productService.reduceAvailabilityByReturnedProduct(productId, quantity);
                        updateSkroutzXml(productId);
                    });
                    break;
                default:
                    return new ResponseEntity<>("Unsupported order state", HttpStatus.BAD_REQUEST);
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
                skroutzFeedService.updateFeed(new File(FEED_PATH), List.of(product));
            }
        } catch (Exception e) {
            log.error("Update feed exception: " + e.getMessage());
        }
    }
}
