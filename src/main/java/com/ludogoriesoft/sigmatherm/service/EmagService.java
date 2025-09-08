package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrder;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrdersCount;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrdersCountResponse;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrdersResponse;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagProduct;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagReturnedOrdersResponse;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagReturnedProduct;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagReturnedResult;
import com.ludogoriesoft.sigmatherm.model.SyncLog;
import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.model.enums.SyncDirection;
import com.ludogoriesoft.sigmatherm.model.enums.SyncOperation;
import com.ludogoriesoft.sigmatherm.exception.EmagException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmagService {

    private static final String AUTHORIZATION = "Authorization";
    private static final String TOKEN_PREFIX = "Basic ";
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String READ_PATH = "/read";
    private static final String COUNT_PATH = "/count";
    private static final String UPDATE_STOCK_PATH = "/api-3/offer_stock/";

    @Value("${emag.api.username}")
    private String username;

    @Value("${emag.api.password}")
    private String password;

    private final RestTemplate restTemplate;
    private final ProductService productService;
    private final SyncLogService syncLogService;

    public void processStockUpdateToEmag(String url, String productId, int stock) {
        Platform platform = determinePlatformFromUrl(url);
        boolean success = false;
        String errorMessage = null;

        try {
            HttpHeaders headers = getHeaders();
            Map<String, Object> body = new HashMap<>();
            body.put("stock", stock);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url + UPDATE_STOCK_PATH + productId,
                    HttpMethod.PATCH,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                success = true;
                String emagNationality = url.substring(url.length() - 3);
                switch (emagNationality) {
                    case ".bg" -> log.info("Emag BG updated successfully!");
                    case ".ro" -> log.info("Emag RO updated successfully!");
                    case ".hu" -> log.info("Emag HU updated successfully!");
                    default -> log.info("Emag updated successfully!");
                }
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("Failed to update stock for product {} to {}", productId, platform, e);
        }

        // Log the stock update operation
        syncLogService.logSingleOperation(
                platform,
                SyncDirection.OUTBOUND,
                SyncOperation.STOCK_UPDATE,
                null,
                success,
                String.format("Stock update for product %s to %d", productId, stock),
                errorMessage
        );
    }

    public void fetchReturnedEmagOrders(String url, Synchronization lastSync, Synchronization currentSync) {
        Platform platform = determinePlatformFromUrl(url);
        String batchId = platform.name().toLowerCase() + "-returns-" + System.currentTimeMillis();

        SyncLog syncLog = syncLogService.startSync(
                platform,
                SyncDirection.INBOUND,
                SyncOperation.RETURNS,
                currentSync,
                batchId
        );

        int totalProcessed = 0;
        int totalSuccessful = 0;
        int totalFailed = 0;

        try {
            EmagReturnedOrdersResponse ordersResponse = getEmagReturnedOrdersResponse(url, lastSync);

            if (ordersResponse != null && ordersResponse.getResults() != null && !ordersResponse.getResults().isEmpty()) {
                log.info("Processing {} returned orders from {}", ordersResponse.getResults().size(), platform);

                for (EmagReturnedResult result : ordersResponse.getResults()) {
                    for (EmagReturnedProduct product : result.getProducts()) {
                        totalProcessed++;
                        try {
                            String productId = product.getProduct_id();
                            int quantity = product.getQuantity();

                            productService.increaseAvailabilityByReturn(productId, quantity);
                            productService.setSync(productId, currentSync);
                            totalSuccessful++;

                            log.debug("Processed returned product {}: {} units", productId, quantity);

                        } catch (Exception e) {
                            totalFailed++;
                            log.error("Failed to process returned product {}", product.getProduct_id(), e);
                        }

                        // Update progress every 10 items
                        if (totalProcessed % 10 == 0) {
                            syncLogService.updateProgress(syncLog.getId(), totalProcessed, totalSuccessful, totalFailed,
                                    String.format("Processed %d returned products", totalProcessed));
                        }
                    }
                }
            } else {
                log.info("No returned orders found for {}", platform);
            }

            String details = String.format("Processed %d returned products from %s", totalProcessed, platform);
            syncLogService.completeSync(syncLog.getId(), totalProcessed, totalSuccessful, totalFailed, details);

            log.info("{} returns synchronized successfully! Processed: {}, Successful: {}, Failed: {}",
                    platform, totalProcessed, totalSuccessful, totalFailed);

        } catch (Exception e) {
            syncLogService.failSync(syncLog.getId(), e.getMessage(), totalProcessed, totalSuccessful, totalFailed);
            log.error("Failed to fetch {} returns", platform, e);
            throw new RuntimeException("Failed to fetch " + platform + " returns", e);
        }
    }

    public void fetchEmagOrders(String url, Synchronization synchronization, Synchronization lastSync) throws EmagException {
        Platform platform = determinePlatformFromUrl(url);
        String batchId = platform.name().toLowerCase() + "-orders-" + System.currentTimeMillis();

        SyncLog syncLog = syncLogService.startSync(
                platform,
                SyncDirection.INBOUND,
                SyncOperation.ORDERS,
                synchronization,
                batchId
        );

        int totalProcessed = 0;
        int totalSuccessful = 0;
        int totalFailed = 0;

        try {
            // Get order count first
            EmagOrdersCountResponse ordersCountResponse = getEmagOrdersCountResponse(url + COUNT_PATH, lastSync);

            if (ordersCountResponse.isError()) {
                String errorMsg = ordersCountResponse.getMessages().get(0);
                log.warn("{} order count error: {}", platform, errorMsg);
                syncLogService.failSync(syncLog.getId(), errorMsg, 0, 0, 0);
                throw new EmagException(errorMsg);
            }

            EmagOrdersCount ordersCount = ordersCountResponse.getResults();
            int totalPages = ordersCount.getNoOfPages();

            log.info("Processing {} pages of orders from {}", totalPages, platform);

            // Process each page
            for (int i = 1; i <= totalPages; i++) {
                try {
                    EmagOrdersResponse response = getEmagOrdersResponse(url + READ_PATH, i, lastSync);

                    if (response.isError()) {
                        String errorMsg = response.getMessages().get(0);
                        log.warn("Error on page {} for {}: {}", i, platform, errorMsg);
                        totalFailed++;
                        continue;
                    }

                    // Process orders on this page
                    for (EmagOrder order : response.getResults()) {
                        for (EmagProduct product : order.getProducts()) {
                            totalProcessed++;
                            try {
                                productService.reduceAvailabilityByOrder(product.getProduct_id(), product.getQuantity());
                                productService.setSync(product.getProduct_id(), synchronization);
                                totalSuccessful++;

                                log.debug("Processed order product {}: {} units", product.getProduct_id(), product.getQuantity());

                            } catch (Exception e) {
                                totalFailed++;
                                log.error("Failed to process product {} in order from {}", product.getProduct_id(), platform, e);
                            }
                        }
                    }

                    // Update progress after each page
                    syncLogService.updateProgress(syncLog.getId(), totalProcessed, totalSuccessful, totalFailed,
                            String.format("Processed page %d/%d (%d products so far)", i, totalPages, totalProcessed));

                    log.debug("Completed page {}/{} for {}", i, totalPages, platform);

                } catch (Exception e) {
                    log.error("Error processing page {} from {}", i, platform, e);
                    totalFailed++;
                }
            }

            // Complete the sync
            String details = String.format("Processed %d pages with %d products from %s", totalPages, totalProcessed, platform);
            syncLogService.completeSync(syncLog.getId(), totalProcessed, totalSuccessful, totalFailed, details);

            log.info("{} orders synchronized successfully! Pages: {}, Products: {}, Successful: {}, Failed: {}",
                    platform, totalPages, totalProcessed, totalSuccessful, totalFailed);

        } catch (EmagException e) {
            // Re-throw EmagException as-is
            throw e;
        } catch (Exception e) {
            syncLogService.failSync(syncLog.getId(), e.getMessage(), totalProcessed, totalSuccessful, totalFailed);
            log.error("Failed to fetch {} orders", platform, e);
            throw new EmagException("Failed to fetch " + platform + " orders: " + e.getMessage());
        }
    }

    private Platform determinePlatformFromUrl(String url) {
        if (url.contains(".bg")) return Platform.eMagBg;
        if (url.contains(".ro")) return Platform.eMagRo;
        if (url.contains(".hu")) return Platform.eMagHu;
        return Platform.eMagBg; // default fallback
    }

    // Keep all existing private methods unchanged
    private EmagOrdersResponse getEmagOrdersResponse(String url, int page, Synchronization lastSync) {
        HttpHeaders headers = getHeaders();
        MultiValueMap<String, String> body = getOrdersRequestBody(page, lastSync);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<EmagOrdersResponse> response =
                restTemplate.postForEntity(url, entity, EmagOrdersResponse.class);
        return response.getBody();
    }

    private EmagReturnedOrdersResponse getEmagReturnedOrdersResponse(String url, Synchronization lastSync) {
        HttpHeaders headers = getHeaders();
        MultiValueMap<String, String> body = getReturnedOrdersRequestBody(lastSync);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<EmagReturnedOrdersResponse> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, EmagReturnedOrdersResponse.class);
        return response.getBody();
    }

    private EmagOrdersCountResponse getEmagOrdersCountResponse(String url, Synchronization lastSync) {
        HttpHeaders headers = getHeaders();
        MultiValueMap<String, String> body = getOrdersRequestBody(1, lastSync);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<EmagOrdersCountResponse> response =
                restTemplate.postForEntity(url, entity, EmagOrdersCountResponse.class);
        return response.getBody();
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTHORIZATION, createBasicAuthHeader());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String createBasicAuthHeader() {
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        return TOKEN_PREFIX + new String(encodedAuth);
    }

    private static MultiValueMap<String, String> getOrdersRequestBody(int page, Synchronization lastSync) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();

        ZoneId zone = ZoneId.of("Europe/Sofia");
        LocalDate today = LocalDate.now(zone).minusDays(1);

        LocalDateTime startTime = today.atStartOfDay();
        LocalDateTime endTime = today.atTime(23, 59, 59);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

        String createdAfter = startTime.format(formatter);
        if (lastSync != null) {
            createdAfter =
                    lastSync
                            .getWriteDate()
                            .atZone(ZoneId.systemDefault())
                            .withZoneSameInstant(zone)
                            .toLocalDateTime()
                            .format(formatter);
        }
        String createdBefore = endTime.format(formatter);

        body.add("createdBefore", createdBefore);
        body.add("createdAfter", createdAfter);
        body.add("status", "4");
        body.add("currentPage", String.valueOf(page));
        return body;
    }

    private static MultiValueMap<String, String> getReturnedOrdersRequestBody(Synchronization lastSync) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();

        ZoneId zone = ZoneId.of("Europe/Sofia");
        LocalDate today = LocalDate.now(zone).minusDays(1);

        LocalDateTime startTime = today.atStartOfDay();
        LocalDateTime endTime = today.atTime(23, 59, 59);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

        String createdAfter = startTime.format(formatter);
        if (lastSync != null) {
            createdAfter =
                    lastSync
                            .getWriteDate()
                            .atZone(ZoneId.systemDefault())
                            .withZoneSameInstant(zone)
                            .toLocalDateTime()
                            .format(formatter);
        }
        String createdBefore = endTime.format(formatter);

        body.add("date_end", createdBefore);
        body.add("date_start", createdAfter);
        body.add("request_status", "3");
        return body;
    }
}