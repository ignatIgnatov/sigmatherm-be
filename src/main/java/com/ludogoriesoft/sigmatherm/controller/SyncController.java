package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.service.SynchronizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/syncs")
public class SyncController {

    private final SynchronizationService synchronizationService;

    @GetMapping
    public ResponseEntity<List<Synchronization>> getAllSyncs() {
        List<Synchronization> response = synchronizationService.getAllSynchronizations();
        return ResponseEntity.ok().body(response);
    }
}
