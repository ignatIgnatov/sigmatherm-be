package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.entity.Synchronization;
import com.ludogoriesoft.sigmatherm.entity.enums.Platform;
import com.ludogoriesoft.sigmatherm.exception.ObjectNotFoundException;
import com.ludogoriesoft.sigmatherm.repository.SynchronizationRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    Synchronization synchronization =
        Synchronization.builder().platform(platform).startDate(LocalDateTime.now()).build();
    return synchronizationRepository.save(synchronization);
  }

  public Synchronization getLastSyncByPlatform(Platform platform) {
    return synchronizationRepository.findTopByPlatformOrderByEndDateDesc(platform).orElse(null);
  }
}
