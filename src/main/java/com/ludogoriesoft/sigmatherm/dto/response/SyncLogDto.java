package com.ludogoriesoft.sigmatherm.dto.response;

import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.model.enums.SyncDirection;
import com.ludogoriesoft.sigmatherm.model.enums.SyncOperation;
import com.ludogoriesoft.sigmatherm.model.enums.SyncStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class SyncLogDto {
    private UUID id;
    private Platform platform;
    private SyncDirection direction;
    private SyncOperation operation;
    private SyncStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer itemsProcessed;
    private Integer itemsSuccessful;
    private Integer itemsFailed;
    private String details;
    private String errorMessage;
    private String batchId;
    private Long durationMs;
    private String durationFormatted;

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
        if (durationMs != null) {
            this.durationFormatted = formatDuration(durationMs);
        }
    }

    private String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        } else if (ms < 60000) {
            return String.format("%.1fs", ms / 1000.0);
        } else {
            long minutes = ms / 60000;
            long seconds = (ms % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
}