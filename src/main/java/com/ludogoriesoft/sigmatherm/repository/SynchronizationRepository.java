package com.ludogoriesoft.sigmatherm.repository;

import com.ludogoriesoft.sigmatherm.entity.Synchronization;
import com.ludogoriesoft.sigmatherm.entity.enums.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SynchronizationRepository extends JpaRepository<Synchronization, UUID> {
    Optional<Synchronization> findTopByPlatformOrderByEndDateDesc(Platform platform);
}
