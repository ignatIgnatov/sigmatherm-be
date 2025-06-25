package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.entity.Product;
import com.ludogoriesoft.sigmatherm.entity.Synchronization;
import com.ludogoriesoft.sigmatherm.entity.enums.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

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
    private final EmagService emagService;
    private final SkroutzFeedService skroutzFeedService;

    @Scheduled(cron = "0 30 23 * * *")
    public void fetchEmagBgOrders() {
        Synchronization lastSync = synchronizationService.getLastSyncByPlatform(Platform.eMagBg);
        Synchronization currentSync = synchronizationService.createSync(Platform.eMagBg);
        emagService.fetchEmagOrders(emagBgUrl + ORDER_URL, currentSync, lastSync);
        emagService.fetchReturnedEmagOrders(emagBgUrl + RETURNED_ORDER_URL, lastSync);
    }

    @Scheduled(cron = "0 32 23 * * *")
    public void fetchEmagRoOrders() {
        Synchronization lastSync = synchronizationService.getLastSyncByPlatform(Platform.eMagRo);
        Synchronization currentSync = synchronizationService.createSync(Platform.eMagRo);
        emagService.fetchEmagOrders(emagRoUrl + ORDER_URL, currentSync, lastSync);
        emagService.fetchReturnedEmagOrders(emagRoUrl + RETURNED_ORDER_URL, lastSync);
    }

    @Scheduled(cron = "0 34 23 * * *")
    public void fetchEmagHuOrders() {
        Synchronization lastSync = synchronizationService.getLastSyncByPlatform(Platform.eMagHu);
        Synchronization currentSync = synchronizationService.createSync(Platform.eMagHu);
        emagService.fetchEmagOrders(emagHuUrl + ORDER_URL, currentSync, lastSync);
        emagService.fetchReturnedEmagOrders(emagHuUrl + RETURNED_ORDER_URL, lastSync);
    }

    @Scheduled(cron = "0 40 23 * * *")
    public void updateStockToStores() throws Exception {
        List<Product> products = productService.getAllProductsSynchronizedToday();

       if (!products.isEmpty()) {
           // To Emag stores
           for (Product product : products) {
               emagService.uploadActualStockToEmag(emagBgUrl, product.getId(), product.getStock());
               emagService.uploadActualStockToEmag(emagRoUrl, product.getId(), product.getStock());
               emagService.uploadActualStockToEmag(emagHuUrl, product.getId(), product.getStock());
           }

           // To Skroutz.gr store
           skroutzFeedService.updateFeed(new File(FEED_PATH), products);
       }
    }
}
