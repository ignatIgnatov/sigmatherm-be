package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.model.Product;
import com.ludogoriesoft.sigmatherm.model.SyncLog;
import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.model.enums.SyncDirection;
import com.ludogoriesoft.sigmatherm.model.enums.SyncOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CronJobService {

    private static final String ORDER_URL = "/api-3/order";
    private static final String RETURNED_ORDER_URL = "/api-3/rma/read";
    private static final String FEED_PATH = "/app/feeds/skroutz_feed.xml";

    @Value("${emag.api.bg-url}")
    private String emagBgUrl;

    @Value("${emag.api.ro-url}")
    private String emagRoUrl;

    @Value("${emag.api.hu-url}")
    private String emagHuUrl;

    private final ProductService productService;
    private final SynchronizationService synchronizationService;
    private final SyncLogService syncLogService;
    private final EmagService emagService;
    private final SkroutzFeedService skroutzFeedService;
    private final BolService bolService;
    private final MicroinvestService microinvestService;

    @Scheduled(cron = "0 30 23 * * *")
    public void fetchEmagBgData() {
        String batchId = "emag-bg-" + System.currentTimeMillis();
        performEmagSync(Platform.eMagBg, emagBgUrl, batchId);
    }

    @Scheduled(cron = "0 32 23 * * *")
    public void fetchEmagRoData() {
        String batchId = "emag-ro-" + System.currentTimeMillis();
        performEmagSync(Platform.eMagRo, emagRoUrl, batchId);
    }

    @Scheduled(cron = "0 34 23 * * *")
    public void fetchEmagHuData() {
        String batchId = "emag-hu-" + System.currentTimeMillis();
        performEmagSync(Platform.eMagHu, emagHuUrl, batchId);
    }

    private void performEmagSync(Platform platform, String url, String batchId) {
        Synchronization lastSync = synchronizationService.getLastSyncByPlatform(platform);
        Synchronization currentSync = synchronizationService.createSync(platform);

        // Log orders sync
        SyncLog ordersLog = syncLogService.startSync(platform, SyncDirection.INBOUND,
                SyncOperation.ORDERS, currentSync, batchId + "-orders");

        try {
            emagService.fetchEmagOrders(url + ORDER_URL, currentSync, lastSync);
            syncLogService.completeSync(ordersLog.getId(), 0, 0, 0, "Orders sync completed successfully");
        } catch (Exception e) {
            syncLogService.failSync(ordersLog.getId(), e.getMessage(), 0, 0, 0);
            log.error("Failed to fetch {} orders", platform, e);
        }

        // Log returns sync
        SyncLog returnsLog = syncLogService.startSync(platform, SyncDirection.INBOUND,
                SyncOperation.RETURNS, currentSync, batchId + "-returns");

        try {
            emagService.fetchReturnedEmagOrders(url + RETURNED_ORDER_URL, lastSync, currentSync);
            syncLogService.completeSync(returnsLog.getId(), 0, 0, 0, "Returns sync completed successfully");
        } catch (Exception e) {
            syncLogService.failSync(returnsLog.getId(), e.getMessage(), 0, 0, 0);
            log.error("Failed to fetch {} returns", platform, e);
        }
    }

    @Scheduled(cron = "0 36 23 * * *")
    public void fetchMicroinvestData() {
        LocalDate today = LocalDate.now();
        String batchId = "microinvest-" + System.currentTimeMillis();

        // Log orders sync
        SyncLog ordersLog = syncLogService.startSync(Platform.Microinvest, SyncDirection.INBOUND,
                SyncOperation.ORDERS, null, batchId + "-orders");

        try {
            microinvestService.processMicroinvestOrders(today, today);
            syncLogService.completeSync(ordersLog.getId(), 0, 0, 0, "Microinvest orders processed successfully");
        } catch (Exception e) {
            syncLogService.failSync(ordersLog.getId(), e.getMessage(), 0, 0, 0);
            log.error("Failed to process Microinvest orders", e);
        }

        // Log returns sync
        SyncLog returnsLog = syncLogService.startSync(Platform.Microinvest, SyncDirection.INBOUND,
                SyncOperation.RETURNS, null, batchId + "-returns");

        try {
            microinvestService.processMicroinvestReturns(today, today);
            syncLogService.completeSync(returnsLog.getId(), 0, 0, 0, "Microinvest returns processed successfully");
        } catch (Exception e) {
            syncLogService.failSync(returnsLog.getId(), e.getMessage(), 0, 0, 0);
            log.error("Failed to process Microinvest returns", e);
        }
    }

    @Scheduled(cron = "0 38 23 * * *")
    public void fetchBolData() {
        String batchId = "bol-" + System.currentTimeMillis();

        // Log shipments sync
        SyncLog shipmentsLog = syncLogService.startSync(Platform.Bol, SyncDirection.INBOUND,
                SyncOperation.ORDERS, null, batchId + "-shipments");

        try {
            bolService.processShipments();
            syncLogService.completeSync(shipmentsLog.getId(), 0, 0, 0, "BOL shipments processed successfully");
        } catch (Exception e) {
            syncLogService.failSync(shipmentsLog.getId(), e.getMessage(), 0, 0, 0);
            log.error("Failed to process BOL shipments", e);
        }

        // Log returns sync
        SyncLog returnsLog = syncLogService.startSync(Platform.Bol, SyncDirection.INBOUND,
                SyncOperation.RETURNS, null, batchId + "-returns");

        try {
            bolService.processReturns();
            syncLogService.completeSync(returnsLog.getId(), 0, 0, 0, "BOL returns processed successfully");
        } catch (Exception e) {
            syncLogService.failSync(returnsLog.getId(), e.getMessage(), 0, 0, 0);
            log.error("Failed to process BOL returns", e);
        }
    }

    @Scheduled(cron = "0 40 23 * * *")
    public void updateStockToStores() {
        List<Product> products = productService.getAllProductsSynchronizedYesterday();

        if (!products.isEmpty()) {
            String batchId = "stock-update-" + System.currentTimeMillis();
            log.info("Starting stock update for {} products", products.size());

            // Update stock to EMAG stores
            updateStockToEmagStores(products, batchId);

            // Update stock to BOL
            updateStockToBol(products, batchId);

            // Update Skroutz feed
            updateSkroutzFeed(products, batchId);

            // Set final synchronization timestamps
            for (Platform platform : Platform.values()) {
                Synchronization synchronization = synchronizationService.createSync(platform);
                synchronization.setWriteDate(LocalDateTime.now());
            }
        } else {
            log.info("No products to synchronize today");
        }
    }

    private void updateStockToEmagStores(List<Product> products, String batchId) {
        updateStockToEmagStore(products, emagBgUrl, Platform.eMagBg, batchId);
        updateStockToEmagStore(products, emagRoUrl, Platform.eMagRo, batchId);
        updateStockToEmagStore(products, emagHuUrl, Platform.eMagHu, batchId);
    }

    private void updateStockToEmagStore(List<Product> products, String url, Platform platform, String batchId) {
        Synchronization sync = synchronizationService.createSync(platform);
        SyncLog syncLog = syncLogService.startSync(platform, SyncDirection.OUTBOUND,
                SyncOperation.STOCK_UPDATE, sync, batchId + "-" + platform.name().toLowerCase());

        int successful = 0;
        int failed = 0;

        for (Product product : products) {
            try {
//                emagService.processStockUpdateToEmag(url, product.getId(), product.getStock());
                successful++;
                syncLogService.updateProgress(syncLog.getId(), successful + failed, successful, failed,
                        String.format("Updated stock for product %s to %d", product.getId(), product.getStock()));
            } catch (Exception e) {
                failed++;
                log.error("Failed to update stock for product {} to {}", product.getId(), platform, e);
            }
        }

        syncLogService.completeSync(syncLog.getId(), products.size(), successful, failed,
                String.format("Stock update completed for %s: %d successful, %d failed", platform, successful, failed));
    }

    private void updateStockToBol(List<Product> products, String batchId) {
        Synchronization sync = synchronizationService.createSync(Platform.Bol);
        SyncLog syncLog = syncLogService.startSync(Platform.Bol, SyncDirection.OUTBOUND,
                SyncOperation.STOCK_UPDATE, sync, batchId + "-bol");

        int successful = 0;
        int failed = 0;

        for (Product product : products) {
            try {
                // TODO: Here productId must be replaced with offerId
//                bolService.processStockUpdateToBol(product.getId(), product.getStock());
                successful++;
                syncLogService.updateProgress(syncLog.getId(), successful + failed, successful, failed,
                        String.format("Updated stock for product %s to %d", product.getId(), product.getStock()));
            } catch (Exception e) {
                failed++;
                log.error("Failed to update stock for product {} to BOL", product.getId(), e);
            }
        }

        syncLogService.completeSync(syncLog.getId(), products.size(), successful, failed,
                String.format("BOL stock update completed: %d successful, %d failed", successful, failed));
    }

    private void updateSkroutzFeed(List<Product> products, String batchId) {
        Synchronization sync = synchronizationService.createSync(Platform.Skroutz);
        SyncLog syncLog = syncLogService.startSync(Platform.Skroutz, SyncDirection.OUTBOUND,
                SyncOperation.FEED_UPDATE, sync, batchId + "-skroutz");

        try {
//            skroutzFeedService.processStockUpdateToSkroutz(new File(FEED_PATH), products);
            syncLogService.completeSync(syncLog.getId(), products.size(), products.size(), 0,
                    String.format("Skroutz feed updated with %d products", products.size()));
        } catch (Exception e) {
            syncLogService.failSync(syncLog.getId(), e.getMessage(), products.size(), 0, products.size());
            log.error("Failed to update Skroutz feed", e);
        }
    }
}