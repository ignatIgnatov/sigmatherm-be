package com.ludogoriesoft.sigmatherm.repository;

import com.ludogoriesoft.sigmatherm.model.WebhookEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, Long> {
    boolean existsByOrderIdAndEventType(String orderId, String eventType);
}
