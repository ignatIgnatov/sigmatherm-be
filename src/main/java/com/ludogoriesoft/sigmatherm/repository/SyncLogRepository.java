package com.ludogoriesoft.sigmatherm.repository;

import com.ludogoriesoft.sigmatherm.model.SyncLog;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.model.enums.SyncDirection;
import com.ludogoriesoft.sigmatherm.model.enums.SyncOperation;
import com.ludogoriesoft.sigmatherm.model.enums.SyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, UUID> {

    // Find recent logs by platform
    List<SyncLog> findByPlatformOrderByStartTimeDesc(Platform platform);

    // Find logs by status
    List<SyncLog> findByStatusOrderByStartTimeDesc(SyncStatus status);

    // Find logs by batch ID
    List<SyncLog> findByBatchIdOrderByStartTime(String batchId);

    // Find running operations (started but not finished)
    @Query("SELECT sl FROM SyncLog sl WHERE sl.status = 'STARTED' AND sl.endTime IS NULL")
    List<SyncLog> findRunningOperations();

    // Find logs within date range
    @Query("SELECT sl FROM SyncLog sl WHERE sl.startTime >= :startDate AND sl.startTime <= :endDate ORDER BY sl.startTime DESC")
    List<SyncLog> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Find logs by multiple criteria with pagination
    @Query("""
        SELECT sl FROM SyncLog sl 
        WHERE (:platform IS NULL OR sl.platform = :platform)
        AND (:direction IS NULL OR sl.direction = :direction)
        AND (:operation IS NULL OR sl.operation = :operation)
        AND (:status IS NULL OR sl.status = :status)
        AND (:startDate IS NULL OR sl.startTime >= :startDate)
        AND (:endDate IS NULL OR sl.startTime <= :endDate)
        ORDER BY sl.startTime DESC
    """)
    Page<SyncLog> findByCriteria(
            @Param("platform") Platform platform,
            @Param("direction") SyncDirection direction,
            @Param("operation") SyncOperation operation,
            @Param("status") SyncStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    // Get latest successful sync by platform and operation
    @Query("""
        SELECT sl FROM SyncLog sl 
        WHERE sl.platform = :platform 
        AND sl.operation = :operation 
        AND sl.direction = :direction
        AND sl.status = 'SUCCESS'
        ORDER BY sl.startTime DESC
        LIMIT 1
    """)
    Optional<SyncLog> findLatestSuccessfulSync(
            @Param("platform") Platform platform,
            @Param("operation") SyncOperation operation,
            @Param("direction") SyncDirection direction
    );

    // Count failed operations in last 24 hours
    @Query("""
        SELECT COUNT(sl) FROM SyncLog sl 
        WHERE sl.status = 'FAILED' 
        AND sl.startTime >= :since
    """)
    long countFailedOperationsSince(@Param("since") LocalDateTime since);

    // Get sync statistics
    @Query("""
        SELECT sl.platform, sl.operation, sl.status, COUNT(sl) as count
        FROM SyncLog sl 
        WHERE sl.startTime >= :since
        GROUP BY sl.platform, sl.operation, sl.status
    """)
    List<Object[]> getSyncStatistics(@Param("since") LocalDateTime since);
}