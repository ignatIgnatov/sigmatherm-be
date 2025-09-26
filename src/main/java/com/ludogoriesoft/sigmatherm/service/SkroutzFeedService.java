package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.skroutz.MyWebStore;
import com.ludogoriesoft.sigmatherm.model.Product;
import com.ludogoriesoft.sigmatherm.model.SyncLog;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.model.enums.SyncDirection;
import com.ludogoriesoft.sigmatherm.model.enums.SyncOperation;
import com.ludogoriesoft.sigmatherm.exception.ObjectNotFoundException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkroutzFeedService {

    private static final String FEED_PATH = "/app/feeds/skroutz_feed.xml";

    private final SyncLogService syncLogService;

    public void processStockUpdateToSkroutz(File sourceXmlFile, List<Product> updatedProducts) throws Exception {
        String batchId = "skroutz-feed-update-" + System.currentTimeMillis();

        SyncLog syncLog = syncLogService.startSync(
                Platform.Skroutz,
                SyncDirection.OUTBOUND,
                SyncOperation.FEED_UPDATE,
                null,
                batchId
        );

        int totalProducts = updatedProducts.size();
        int successfulProducts = 0;
        int failedProducts = 0;
        int updatedInFeed = 0;

        try {
            // Validate source file exists
            if (!sourceXmlFile.exists()) {
                String errorMsg = "Source XML file not found: " + sourceXmlFile.getAbsolutePath();
                log.error(errorMsg);
                syncLogService.failSync(syncLog.getId(), errorMsg, 0, 0, 0);
                throw new ObjectNotFoundException(errorMsg);
            }

            log.info("Starting Skroutz feed update with {} products", totalProducts);

            // Parse existing XML file
            JAXBContext context = JAXBContext.newInstance(MyWebStore.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            MyWebStore store = (MyWebStore) unmarshaller.unmarshal(sourceXmlFile);

            log.debug("Loaded existing XML feed with {} products",
                    store.getProducts() != null ? store.getProducts().size() : 0);

            // Update products in the feed
            for (Product p : updatedProducts) {
                try {
                    boolean productFound = false;

                    if (store.getProducts() != null) {
                        for (var feedProduct : store.getProducts()) {
                            if (p.getId().equals(feedProduct.getMpn())) {
                                int oldQuantity = feedProduct.getQuantity();
                                feedProduct.setQuantity(p.getStock());
                                productFound = true;
                                updatedInFeed++;

                                log.debug("Updated product {} in feed: stock {} -> {}",
                                        p.getId(), oldQuantity, p.getStock());
                                break;
                            }
                        }
                    }

                    if (productFound) {
                        successfulProducts++;
                    } else {
                        failedProducts++;
                        log.warn("Product {} not found in Skroutz feed", p.getId());
                    }

                    // Update progress every 50 products
                    if ((successfulProducts + failedProducts) % 50 == 0) {
                        syncLogService.updateProgress(
                                syncLog.getId(),
                                successfulProducts + failedProducts,
                                successfulProducts,
                                failedProducts,
                                String.format("Updated %d/%d products in feed",
                                        successfulProducts + failedProducts, totalProducts)
                        );
                    }

                } catch (Exception e) {
                    failedProducts++;
                    log.error("Failed to update product {} in feed: {}", p.getId(), e.getMessage(), e);
                }
            }

            // Update feed timestamp
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            store.setCreatedAt(now);

            // Write updated XML file
            File output = new File(FEED_PATH);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(store, output);

            // Complete the sync log
            String details = String.format(
                    "Skroutz feed updated: %d products processed, %d updated in feed, %d not found. Feed saved to %s",
                    totalProducts, updatedInFeed, failedProducts, FEED_PATH
            );

            syncLogService.completeSync(syncLog.getId(), totalProducts, successfulProducts, failedProducts, details);

            log.info("Skroutz feed update completed: {} products processed, {} updated, {} failed. Feed updated at: {}",
                    totalProducts, successfulProducts, failedProducts, FEED_PATH);

            if (failedProducts > 0) {
                log.warn("{} products were not found in the Skroutz feed", failedProducts);
            }

        } catch (ObjectNotFoundException e) {
            // Re-throw this specific exception
            throw e;
        } catch (Exception e) {
            log.error("Failed to update Skroutz feed", e);

            syncLogService.failSync(
                    syncLog.getId(),
                    e.getMessage(),
                    successfulProducts + failedProducts,
                    successfulProducts,
                    failedProducts
            );

            throw new Exception("Failed to update Skroutz feed: " + e.getMessage(), e);
        }
    }

    public File getFeedFile() {
        // Log when someone requests the feed file (optional)
        syncLogService.logSingleOperation(
                Platform.Skroutz,
                SyncDirection.OUTBOUND,
                SyncOperation.FEED_UPDATE,
                null,
                true,
                "Skroutz feed file requested",
                null
        );

        return Paths.get(FEED_PATH).toFile();
    }
}