package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.magento.MagentoProductResponseDto;
import com.ludogoriesoft.sigmatherm.dto.magento.MagentoProductSalesDto;
import com.ludogoriesoft.sigmatherm.model.Product;
import com.ludogoriesoft.sigmatherm.model.SyncLog;
import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.model.enums.SyncDirection;
import com.ludogoriesoft.sigmatherm.model.enums.SyncOperation;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MagentoService {

    private final SynchronizationService synchronizationService;
    private final SyncLogService syncLogService;
    private final ProductService productService;

    public List<MagentoProductResponseDto> getAllSyncProducts() {
        // Log the outbound operation to provide stock data to Magento
        List<Product> products = productService.getAllProductsSynchronizedYesterday();
        List<MagentoProductResponseDto> response = products.stream()
                .map(this::convertToMagentoProductResponseDto)
                .toList();

        // Log the stock data export operation
        syncLogService.logSingleOperation(
                Platform.Magento,
                SyncDirection.OUTBOUND,
                SyncOperation.STOCK_UPDATE,
                null,
                true,
                String.format("Exported stock data for %d products to Magento", response.size()),
                null
        );

        log.info("Exported {} products stock data to Magento", response.size());
        return response;
    }

    @Transactional
    public ResponseEntity<String> receiveProductSales(List<MagentoProductSalesDto> products) {
        String batchId = "magento-sales-" + System.currentTimeMillis();

        // Validate input first
        if (products.isEmpty()) {
            log.error("Empty list of product sales from Magento");

            syncLogService.logSingleOperation(
                    Platform.Magento,
                    SyncDirection.INBOUND,
                    SyncOperation.ORDERS,
                    null,
                    false,
                    "Received empty product sales list",
                    "Empty list of product sales"
            );

            return ResponseEntity.badRequest().body("Empty list of product sales");
        }

        // Start logging the sales sync operation
        Synchronization synchronization = synchronizationService.createSync(Platform.Magento);
        SyncLog syncLog = syncLogService.startSync(
                Platform.Magento,
                SyncDirection.INBOUND,
                SyncOperation.ORDERS,
                synchronization,
                batchId
        );

        int totalProducts = products.size();
        int successfulProducts = 0;
        int failedProducts = 0;

        try {
            log.info("Processing {} product sales from Magento", totalProducts);

            for (MagentoProductSalesDto product : products) {
                try {
                    productService.reduceAvailabilityByOrder(product.getId(), product.getSales());
                    productService.setSync(product.getId(), synchronization);
                    successfulProducts++;

                    log.debug("Processed sale for product {}: {} units", product.getId(), product.getSales());

                    // Update progress every 10 products
                    if ((successfulProducts + failedProducts) % 10 == 0) {
                        syncLogService.updateProgress(
                                syncLog.getId(),
                                successfulProducts + failedProducts,
                                successfulProducts,
                                failedProducts,
                                String.format("Processed %d/%d product sales",
                                        successfulProducts + failedProducts, totalProducts)
                        );
                    }

                } catch (Exception e) {
                    failedProducts++;
                    log.error("Failed to process sale for product {}: {}", product.getId(), e.getMessage(), e);
                }
            }

            // Complete the sync log
            String details = String.format("Processed %d product sales from Magento", totalProducts);
            syncLogService.completeSync(syncLog.getId(), totalProducts, successfulProducts, failedProducts, details);

            if (failedProducts > 0) {
                log.warn("Magento sales sync completed with {} failures out of {} products",
                        failedProducts, totalProducts);
                return ResponseEntity.status(206) // Partial Content
                        .body(String.format("Partial success: %d successful, %d failed",
                                successfulProducts, failedProducts));
            } else {
                log.info("Successfully processed all {} product sales from Magento", totalProducts);
                return ResponseEntity.ok("Product sales received successfully!");
            }

        } catch (Exception e) {
            log.error("Failed to sync sales from Magento: {}", e.getMessage(), e);

            syncLogService.failSync(syncLog.getId(), e.getMessage(),
                    successfulProducts + failedProducts, successfulProducts, failedProducts);

            return ResponseEntity.badRequest().body("Failed to sync the sales: " + e.getMessage());
        }
    }

    private MagentoProductResponseDto convertToMagentoProductResponseDto(Product product) {
        MagentoProductResponseDto productResponseDto = new MagentoProductResponseDto();
        productResponseDto.setId(product.getId());
        productResponseDto.setStock(product.getStock());
        return productResponseDto;
    }
}