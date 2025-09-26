package com.ludogoriesoft.sigmatherm.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_event_log", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"orderId", "eventType"})
})
@Data
public class WebhookEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderId;

    private String eventType;

    private LocalDateTime receivedAt = LocalDateTime.now();
}
