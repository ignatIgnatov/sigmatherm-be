package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.model.Product;
import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final BolService bolService;
    private final MicroinvestService microinvestService;

    @Scheduled(cron = "0 30 23 * * *")
    public void fetchEmagBgData() {
        Synchronization lastSync = synchronizationService.getLastSyncByPlatform(Platform.eMagBg);
        Synchronization currentSync = synchronizationService.createSync(Platform.eMagBg);
        emagService.fetchEmagOrders(emagBgUrl + ORDER_URL, currentSync, lastSync);
        emagService.fetchReturnedEmagOrders(emagBgUrl + RETURNED_ORDER_URL, lastSync, currentSync);
    }

    @Scheduled(cron = "0 32 23 * * *")
    public void fetchEmagRoData() {
        Synchronization lastSync = synchronizationService.getLastSyncByPlatform(Platform.eMagRo);
        Synchronization currentSync = synchronizationService.createSync(Platform.eMagRo);
        emagService.fetchEmagOrders(emagRoUrl + ORDER_URL, currentSync, lastSync);
        emagService.fetchReturnedEmagOrders(emagRoUrl + RETURNED_ORDER_URL, lastSync, currentSync);
    }

    @Scheduled(cron = "0 34 23 * * *")
    public void fetchEmagHuData() {
        Synchronization lastSync = synchronizationService.getLastSyncByPlatform(Platform.eMagHu);
        Synchronization currentSync = synchronizationService.createSync(Platform.eMagHu);
        emagService.fetchEmagOrders(emagHuUrl + ORDER_URL, currentSync, lastSync);
        emagService.fetchReturnedEmagOrders(emagHuUrl + RETURNED_ORDER_URL, lastSync, currentSync);
    }

    @Scheduled(cron = "0 36 23 * * *")
    public void fetchMicroinvestData() {
        LocalDate today = LocalDate.now();
        microinvestService.processMicroinvestOrders(today, today);
        microinvestService.processMicroinvestReturns(today, today);
    }

    @Scheduled(cron = "0 38 23 * * *")
    public void fetchBolData() {
        bolService.processShipments();
        bolService.processReturns();
    }

    @Scheduled(cron = "0 40 23 * * *")
    public void updateStockToStores() throws Exception {
        List<Product> products = productService.getAllProductsSynchronizedToday();

       if (!products.isEmpty()) {
           Synchronization emagBgSync = synchronizationService.createSync(Platform.eMagBg);
           Synchronization emagRoSync = synchronizationService.createSync(Platform.eMagRo);
           Synchronization emagHuSync = synchronizationService.createSync(Platform.eMagHu);
           Synchronization skroutzSync = synchronizationService.createSync(Platform.Skroutz);
           Synchronization magentoSync = synchronizationService.createSync(Platform.Magento);
           Synchronization bolSync = synchronizationService.createSync(Platform.Bol);
           Synchronization microinvestSync = synchronizationService.createSync(Platform.Microinvest);

           for (Product product : products) {
               // To Emag stores
//               emagService.processStockUpdateToEmag(emagBgUrl, product.getId(), product.getStock());
//               emagService.processStockUpdateToEmag(emagRoUrl, product.getId(), product.getStock());
//               emagService.processStockUpdateToEmag(emagHuUrl, product.getId(), product.getStock());

               // To Bol.com store
               // TODO: Here porductId must be replaced with offerId
//               bolService.processStockUpdateToBol(product.getId(), product.getStock());
           }

           // To Skroutz.gr store
//           skroutzFeedService.processStockUpdateToSkroutz(new File(FEED_PATH), products);

           // To Microinvest store
           // TODO:


           for(Platform platform : Platform.values()) {
               Synchronization synchronization = synchronizationService.createSync(platform);
               synchronization.setWriteDate(LocalDateTime.now());
           }
       }
    }
}
