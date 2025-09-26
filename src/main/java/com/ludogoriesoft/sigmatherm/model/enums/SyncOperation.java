package com.ludogoriesoft.sigmatherm.model.enums;

public enum SyncOperation {
    ORDERS,           // Processing orders/sales
    RETURNS,          // Processing returns/refunds
    STOCK_UPDATE,     // Stock/inventory updates
    FEED_UPDATE,      // Feed file updates (like Skroutz XML)
    FULL_SYNC,        // Complete synchronization
    PRODUCT_IMPORT,   // Product imports (like Microinvest items)
    AUTH_TOKEN        // Authentication token operations
}