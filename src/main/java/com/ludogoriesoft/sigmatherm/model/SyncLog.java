package com.ludogoriesoft.sigmatherm.model;

import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.model.enums.SyncDirection;
import com.ludogoriesoft.sigmatherm.model.enums.SyncOperation;
import com.ludogoriesoft.sigmatherm.model.enums.SyncStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sync_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncDirection direction; // INBOUND, OUTBOUND

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncOperation operation; // ORDERS, RETURNS, STOCK_UPDATE, FULL_SYNC

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncStatus status; // STARTED, SUCCESS, FAILED, PARTIAL_SUCCESS

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column
    private LocalDateTime endTime;

    @Column
    private Integer itemsProcessed = 0;

    @Column
    private Integer itemsSuccessful = 0;

    @Column
    private Integer itemsFailed = 0;

    @Column(columnDefinition = "TEXT")
    private String details; // JSON or descriptive text

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    private String batchId; // For grouping related operations

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "synchronization_id")
    private Synchronization synchronization;

    @Column
    private Long durationMs;

    @PreUpdate
    @PrePersist
    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }
}