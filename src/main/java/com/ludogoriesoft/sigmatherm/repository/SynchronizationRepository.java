package com.ludogoriesoft.sigmatherm.repository;

import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SynchronizationRepository extends JpaRepository<Synchronization, UUID> {
    Optional<Synchronization> findTopByPlatformOrderByReadDateDesc(Platform platform);

    @Query("""
            SELECT s FROM Synchronization s
            WHERE s.platform = :platform
            AND s.readDate >= :startOfDay
            AND s.readDate < :endOfDay
            """)
    Optional<Synchronization> findTodaySynchronizationByPlatform(Platform platform, LocalDateTime startOfDay, LocalDateTime endOfDay);
}
