package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.dto.response.SyncLogDto;
import com.ludogoriesoft.sigmatherm.model.SyncLog;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.model.enums.SyncDirection;
import com.ludogoriesoft.sigmatherm.model.enums.SyncOperation;
import com.ludogoriesoft.sigmatherm.model.enums.SyncStatus;
import com.ludogoriesoft.sigmatherm.service.SyncLogService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sync-logs")
@RequiredArgsConstructor
public class SyncLogController {

    private final SyncLogService syncLogService;
    private final ModelMapper modelMapper;

    @GetMapping
    public ResponseEntity<Page<SyncLogDto>> getSyncLogs(
            @RequestParam(required = false) Platform platform,
            @RequestParam(required = false) SyncDirection direction,
            @RequestParam(required = false) SyncOperation operation,
            @RequestParam(required = false) SyncStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<SyncLog> syncLogs = syncLogService.getSyncLogs(platform, direction, operation, status, startDate, endDate, pageable);

        Page<SyncLogDto> syncLogDtos = syncLogs.map(this::convertToDto);
        return ResponseEntity.ok(syncLogDtos);
    }

    @GetMapping("/platform/{platform}")
    public ResponseEntity<List<SyncLogDto>> getLogsByPlatform(@PathVariable Platform platform) {
        List<SyncLog> logs = syncLogService.getRecentLogsByPlatform(platform);
        List<SyncLogDto> dtos = logs.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/failed")
    public ResponseEntity<List<SyncLogDto>> getFailedOperations() {
        List<SyncLog> logs = syncLogService.getFailedOperations();
        List<SyncLogDto> dtos = logs.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/running")
    public ResponseEntity<List<SyncLogDto>> getRunningOperations() {
        List<SyncLog> logs = syncLogService.getRunningOperations();
        List<SyncLogDto> dtos = logs.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/statistics")
    public ResponseEntity<List<Object[]>> getSyncStatistics(
            @RequestParam(defaultValue = "7") int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> stats = syncLogService.getSyncStatistics(since);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/failed-count")
    public ResponseEntity<Long> getFailedOperationsCount(
            @RequestParam(defaultValue = "24") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        long count = syncLogService.getFailedOperationsCount(since);
        return ResponseEntity.ok(count);
    }

    private SyncLogDto convertToDto(SyncLog syncLog) {
        SyncLogDto dto = modelMapper.map(syncLog, SyncLogDto.class);
        dto.setDurationMs(syncLog.getDurationMs()); // This will also set the formatted duration
        return dto;
    }
}