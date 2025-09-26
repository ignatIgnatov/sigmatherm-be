package com.ludogoriesoft.sigmatherm.model.enums;

public enum SyncStatus {
    STARTED,          // Sync operation has started
    SUCCESS,          // Completed successfully
    FAILED,           // Failed completely
    PARTIAL_SUCCESS,  // Some items succeeded, some failed
    CANCELLED,        // Operation was cancelled
    TIMEOUT           // Operation timed out
}