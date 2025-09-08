package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.model.SyncLog;
import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.model.enums.SyncDirection;
import com.ludogoriesoft.sigmatherm.model.enums.SyncOperation;
import com.ludogoriesoft.sigmatherm.model.enums.SyncStatus;
import com.ludogoriesoft.sigmatherm.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncLogService {

    private final SyncLogRepository syncLogRepository;

    /**
     * Start a new sync operation
     */
    public SyncLog startSync(Platform platform, SyncDirection direction, SyncOperation operation,
                             Synchronization synchronization, String batchId) {
        SyncLog syncLog = SyncLog.builder()
                .platform(platform)
                .direction(direction)
                .operation(operation)
                .status(SyncStatus.STARTED)
                .startTime(LocalDateTime.now())
                .synchronization(synchronization)
                .batchId(batchId)
                .itemsProcessed(0)
                .itemsSuccessful(0)
                .itemsFailed(0)
                .build();

        SyncLog saved = syncLogRepository.save(syncLog);
        log.info("Started {} {} sync for {} - ID: {}", direction, operation, platform, saved.getId());
        return saved;
    }

    /**
     * Update sync progress
     */
    public void updateProgress(UUID syncLogId, int itemsProcessed, int itemsSuccessful, int itemsFailed, String details) {
        syncLogRepository.findById(syncLogId).ifPresent(syncLog -> {
            syncLog.setItemsProcessed(itemsProcessed);
            syncLog.setItemsSuccessful(itemsSuccessful);
            syncLog.setItemsFailed(itemsFailed);
            if (details != null) {
                syncLog.setDetails(details);
            }
            syncLogRepository.save(syncLog);
        });
    }

    /**
     * Complete sync operation successfully
     */
    public void completeSync(UUID syncLogId, int totalProcessed, int successful, int failed, String details) {
        syncLogRepository.findById(syncLogId).ifPresent(syncLog -> {
            syncLog.setEndTime(LocalDateTime.now());
            syncLog.setItemsProcessed(totalProcessed);
            syncLog.setItemsSuccessful(successful);
            syncLog.setItemsFailed(failed);

            // Determine final status
            if (failed == 0) {
                syncLog.setStatus(SyncStatus.SUCCESS);
            } else if (successful > 0) {
                syncLog.setStatus(SyncStatus.PARTIAL_SUCCESS);
            } else {
                syncLog.setStatus(SyncStatus.FAILED);
            }

            if (details != null) {
                syncLog.setDetails(details);
            }

            syncLog.calculateDuration();
            SyncLog saved = syncLogRepository.save(syncLog);

            log.info("Completed {} {} sync for {} - Status: {}, Processed: {}, Success: {}, Failed: {}, Duration: {}ms",
                    saved.getDirection(), saved.getOperation(), saved.getPlatform(),
                    saved.getStatus(), totalProcessed, successful, failed, saved.getDurationMs());
        });
    }

    /**
     * Fail sync operation
     */
    public void failSync(UUID syncLogId, String errorMessage, int processed, int successful, int failed) {
        syncLogRepository.findById(syncLogId).ifPresent(syncLog -> {
            syncLog.setEndTime(LocalDateTime.now());
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setErrorMessage(errorMessage);
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsSuccessful(successful);
            syncLog.setItemsFailed(failed);
            syncLog.calculateDuration();

            SyncLog saved = syncLogRepository.save(syncLog);
            log.error("Failed {} {} sync for {} - Error: {}, Duration: {}ms",
                    saved.getDirection(), saved.getOperation(), saved.getPlatform(),
                    errorMessage, saved.getDurationMs());
        });
    }

    /**
     * Cancel sync operation
     */
    public void cancelSync(UUID syncLogId, String reason) {
        syncLogRepository.findById(syncLogId).ifPresent(syncLog -> {
            syncLog.setEndTime(LocalDateTime.now());
            syncLog.setStatus(SyncStatus.CANCELLED);
            syncLog.setErrorMessage(reason);
            syncLog.calculateDuration();
            syncLogRepository.save(syncLog);

            log.warn("Cancelled {} {} sync for {} - Reason: {}",
                    syncLog.getDirection(), syncLog.getOperation(), syncLog.getPlatform(), reason);
        });
    }

    /**
     * Timeout sync operation
     */
    public void timeoutSync(UUID syncLogId) {
        syncLogRepository.findById(syncLogId).ifPresent(syncLog -> {
            syncLog.setEndTime(LocalDateTime.now());
            syncLog.setStatus(SyncStatus.TIMEOUT);
            syncLog.setErrorMessage("Operation timed out");
            syncLog.calculateDuration();
            syncLogRepository.save(syncLog);

            log.warn("Timeout {} {} sync for {} after {}ms",
                    syncLog.getDirection(), syncLog.getOperation(), syncLog.getPlatform(),
                    syncLog.getDurationMs());
        });
    }

    /**
     * Log a single item operation (for stock updates, single product syncs, etc.)
     */
    public SyncLog logSingleOperation(Platform platform, SyncDirection direction, SyncOperation operation,
                                      Synchronization synchronization, boolean success, String details, String errorMessage) {
        SyncLog syncLog = SyncLog.builder()
                .platform(platform)
                .direction(direction)
                .operation(operation)
                .status(success ? SyncStatus.SUCCESS : SyncStatus.FAILED)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .synchronization(synchronization)
                .itemsProcessed(1)
                .itemsSuccessful(success ? 1 : 0)
                .itemsFailed(success ? 0 : 1)
                .details(details)
                .errorMessage(errorMessage)
                .build();

        syncLog.calculateDuration();
        SyncLog saved = syncLogRepository.save(syncLog);

        log.info("Logged single {} {} operation for {} - Status: {}",
                direction, operation, platform, success ? "SUCCESS" : "FAILED");

        return saved;
    }

    // Query methods
    public List<SyncLog> getRecentLogsByPlatform(Platform platform) {
        return syncLogRepository.findByPlatformOrderByStartTimeDesc(platform);
    }

    public List<SyncLog> getFailedOperations() {
        return syncLogRepository.findByStatusOrderByStartTimeDesc(SyncStatus.FAILED);
    }

    public List<SyncLog> getRunningOperations() {
        return syncLogRepository.findRunningOperations();
    }

    public Page<SyncLog> getSyncLogs(Platform platform, SyncDirection direction, SyncOperation operation,
                                     SyncStatus status, LocalDateTime startDate, LocalDateTime endDate,
                                     Pageable pageable) {
        return syncLogRepository.findByCriteria(platform, direction, operation, status, startDate, endDate, pageable);
    }

    public Optional<SyncLog> getLatestSuccessfulSync(Platform platform, SyncOperation operation, SyncDirection direction) {
        return syncLogRepository.findLatestSuccessfulSync(platform, operation, direction);
    }

    public long getFailedOperationsCount(LocalDateTime since) {
        return syncLogRepository.countFailedOperationsSince(since);
    }

    public List<Object[]> getSyncStatistics(LocalDateTime since) {
        return syncLogRepository.getSyncStatistics(since);
    }

    /**
     * Cleanup old logs (optional - for maintenance)
     */
    public int cleanupOldLogs(LocalDateTime olderThan) {
        List<SyncLog> oldLogs = syncLogRepository.findByDateRange(LocalDateTime.of(2000, 1, 1, 0, 0), olderThan);
        syncLogRepository.deleteAll(oldLogs);
        log.info("Cleaned up {} old sync logs older than {}", oldLogs.size(), olderThan);
        return oldLogs.size();
    }
}