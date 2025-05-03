package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.dto.SkroutzOrderWebhook;
import com.ludogoriesoft.sigmatherm.entity.enums.EventType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/skroutz-orders")
public class SkroutzWebhookController {

  @PostMapping
  public ResponseEntity<String> receiveOrder(@RequestBody SkroutzOrderWebhook webhook) {
    EventType type = webhook.getEvent_type();

    if (type == null) {
      return ResponseEntity.badRequest().body("Invalid or missing event_type");
    }

    System.out.println("📥 Получен webhook: " + type);

    switch (type) {
      case new_order:
        // обработка на нова поръчка
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
