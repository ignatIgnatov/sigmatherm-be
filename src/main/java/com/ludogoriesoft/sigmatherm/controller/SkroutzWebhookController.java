package com.ludogoriesoft.sigmatherm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludogoriesoft.sigmatherm.dto.SkroutzOrderWebhook;
import com.ludogoriesoft.sigmatherm.entity.enums.EventType;
import com.ludogoriesoft.sigmatherm.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/skroutz-orders")
@RequiredArgsConstructor
public class SkroutzWebhookController {

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @Value("${skroutz.webhook.secret}")
    private String skroutzSecret;

    @PostMapping
    public ResponseEntity<String> receiveOrder(@RequestBody String rawPayload) {

        log.debug("Received raw webhook payload: {}", rawPayload);

        try {
            SkroutzOrderWebhook webhook = objectMapper.readValue(rawPayload, SkroutzOrderWebhook.class);
            EventType type = webhook.getEvent_type();

            if (type == null) {
                return ResponseEntity.badRequest().body("Invalid or missing event_type");
            }

            log.info(LocalDateTime.now() + " ---> Received skroutz webhook type: {}", type);

            switch (type) {
                case new_order:
                    webhook.getOrder().getLine_items().forEach(line -> {
                        String productId = line.getId();
                        int quantity = line.getQuantity();
                        log.info("New Order Product ID: " + productId);
                        log.info("New Order Sale quantity: " + quantity);
            productService.reduceAvailability(productId, quantity);
                    });
                    break;
                case order_updated:
                    webhook.getOrder().getLine_items().forEach(line -> {
                        String productId = line.getId();
                        int quantity = line.getQuantity();
                        log.info("Order Update Product ID: " + productId);
                        log.info("Order Update quantity: " + quantity);
//            productService.reduceAvailability(productId, quantity);
                    });
                    break;
                default:
                    return ResponseEntity.badRequest().body("Unsupported event_type");
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Error processing Skroutz webhook: ", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }
}
