package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.entity.Synchronization;
import com.ludogoriesoft.sigmatherm.entity.enums.Platform;
import com.ludogoriesoft.sigmatherm.exception.ObjectNotFoundException;
import com.ludogoriesoft.sigmatherm.repository.SynchronizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SynchronizationService {
    private final SynchronizationRepository synchronizationRepository;

    public Synchronization findById(UUID id) {
        return synchronizationRepository
                .findById(id)
                .orElseThrow(
                        () -> new ObjectNotFoundException("Synchronization with id " + id + " not found"));
    }

    public void setEndDate(Synchronization synchronization) {
        synchronization.setEndDate(LocalDateTime.now());
        synchronizationRepository.save(synchronization);
    }

    public Synchronization createSync(Platform platform) {
        Synchronization synchronization = findSynchronizationFromToday(platform);
        if (synchronization == null) {
            synchronization =
                    Synchronization.builder().platform(platform).startDate(LocalDateTime.now()).build();
        }
        return synchronizationRepository.save(synchronization);
    }

    public Synchronization getLastSyncByPlatform(Platform platform) {
        return synchronizationRepository.findTopByPlatformOrderByEndDateDesc(platform).orElse(null);
    }

    public Synchronization findSynchronizationFromToday(Platform platform) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        return synchronizationRepository.findTodaySynchronizationByPlatform(platform, startOfDay, endOfDay).orElse(null);
    }
}
