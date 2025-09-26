package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.repository.SynchronizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SynchronizationService {
    private final SynchronizationRepository synchronizationRepository;

    public void setWriteDate(Synchronization synchronization) {
        synchronization.setWriteDate(LocalDateTime.now());
        synchronizationRepository.save(synchronization);
    }

    public Synchronization createSync(Platform platform) {
        Synchronization synchronization = findSynchronizationFromToday(platform);
        if (synchronization == null) {
            log.info("Created new sync for {} platform", platform);
            synchronization =
                    Synchronization.builder().platform(platform).readDate(LocalDateTime.now()).build();
        } else {
            log.info("Using an existing sync for {} platform", platform);
        }
        return synchronizationRepository.save(synchronization);
    }

    public Synchronization getLastSyncByPlatform(Platform platform) {
        return synchronizationRepository.findTopByPlatformOrderByReadDateDesc(platform).orElse(null);
    }

    public Synchronization findSynchronizationFromToday(Platform platform) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        return synchronizationRepository.findTodaySynchronizationByPlatform(platform, startOfDay, endOfDay).orElse(null);
    }

    public List<Synchronization> getAllSynchronizations() {
        return synchronizationRepository.findAll();
    }
}
