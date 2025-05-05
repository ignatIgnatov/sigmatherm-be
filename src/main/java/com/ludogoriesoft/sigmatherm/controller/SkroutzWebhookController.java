package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.dto.SkroutzOrderWebhook;
import com.ludogoriesoft.sigmatherm.entity.enums.EventType;
import com.ludogoriesoft.sigmatherm.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/skroutz-orders")
@RequiredArgsConstructor
public class SkroutzWebhookController {

  private final ProductService productService;

  @PostMapping
  public ResponseEntity<String> receiveOrder(@RequestBody SkroutzOrderWebhook webhook) {
    EventType type = webhook.getEvent_type();

    if (type == null) {
      return ResponseEntity.badRequest().body("Invalid or missing event_type");
    }

    System.out.println("📥 Получен webhook: " + type);

    switch (type) {
      case new_order:
        //        webhook
        //            .getOrder()
        //            .getOrder_lines()
        //            .forEach(
        //                line -> {
        //                  String productId = line.getShopUid();
        //                  int orderedQuantity = line.getQuantity();
        //
        //                  productService.reduceAvailability(productId, orderedQuantity);
        //                });
        break;
      case order_updated:
        // обработка на промяна на поръчка
        break;
      default:
        return ResponseEntity.badRequest().body("Unsupported event_type");
    }

    return ResponseEntity.ok("OK");
  }
}
