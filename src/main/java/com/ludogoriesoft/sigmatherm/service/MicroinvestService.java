package com.ludogoriesoft.sigmatherm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludogoriesoft.sigmatherm.dto.microinvest.ItemDto;
import com.ludogoriesoft.sigmatherm.dto.microinvest.OperationDto;
import com.ludogoriesoft.sigmatherm.dto.microinvest.StoreDto;
import com.ludogoriesoft.sigmatherm.dto.request.ProductRequest;
import com.ludogoriesoft.sigmatherm.model.SyncLog;
import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.model.enums.SyncDirection;
import com.ludogoriesoft.sigmatherm.model.enums.SyncOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MicroinvestService {

    private final WebClient webClient;
    private final SynchronizationService synchronizationService;
    private final SyncLogService syncLogService;
    private final ProductService productService;
    private final BrandService brandService;

    private static final Integer SALE_OPERATION_TYPE = 2;
    private static final Integer STORNO_OPERATION_TYPE = 34;

    public MicroinvestService(@Value("${microinvest.api.url}") String baseUrl,
                              SynchronizationService synchronizationService,
                              SyncLogService syncLogService,
                              ProductService productService,
                              BrandService brandService) {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .baseUrl(baseUrl)
                .build();
        this.synchronizationService = synchronizationService;
        this.syncLogService = syncLogService;
        this.productService = productService;
        this.brandService = brandService;
    }

    public void processMicroinvestOrders(LocalDate from, LocalDate to) {
        String batchId = "microinvest-orders-" + System.currentTimeMillis();
        Synchronization sync = synchronizationService.createSync(Platform.Microinvest);

        SyncLog syncLog = syncLogService.startSync(
                Platform.Microinvest,
                SyncDirection.INBOUND,
                SyncOperation.ORDERS,
                sync,
                batchId
        );

        int processedItems = 0;
        int successfulItems = 0;
        int failedItems = 0;

        try {
            Optional<List<OperationDto>> ordersOpt = fetchOperationsFromMicroinvestApi(SALE_OPERATION_TYPE, from, to);

            if (ordersOpt.isEmpty() || ordersOpt.get().isEmpty()) {
                log.info("No Microinvest orders found for period {} to {}", from, to);
                syncLogService.completeSync(syncLog.getId(), 0, 0, 0, "No orders found for the specified period");
                return;
            }

            List<OperationDto> orders = ordersOpt.get();
            log.info("Processing {} Microinvest orders", orders.size());

            for (OperationDto order : orders) {
                processedItems++;
                try {
                    productService.reduceAvailabilityByOrder(order.getGoodId(), order.getQuantity());
                    productService.setSync(order.getGoodId(), sync);
                    successfulItems++;

                    log.debug("Processed order for product {}: {} units", order.getGoodId(), order.getQuantity());

                    // Update progress every 10 items
                    if (processedItems % 10 == 0) {
                        syncLogService.updateProgress(
                                syncLog.getId(),
                                processedItems,
                                successfulItems,
                                failedItems,
                                String.format("Processed %d/%d Microinvest orders", processedItems, orders.size())
                        );
                    }

                } catch (Exception e) {
                    failedItems++;
                    log.error("Failed to process Microinvest order for product {}: {}",
                            order.getGoodId(), e.getMessage(), e);
                }
            }

            String details = String.format("Processed Microinvest orders from %s to %s", from, to);
            syncLogService.completeSync(syncLog.getId(), processedItems, successfulItems, failedItems, details);

            log.info("Microinvest orders processing completed: {} total, {} successful, {} failed",
                    processedItems, successfulItems, failedItems);

        } catch (Exception e) {
            log.error("Failed to process Microinvest orders", e);
            syncLogService.failSync(syncLog.getId(), e.getMessage(), processedItems, successfulItems, failedItems);
        }
    }

    public void processMicroinvestReturns(LocalDate from, LocalDate to) {
        String batchId = "microinvest-returns-" + System.currentTimeMillis();
        Synchronization sync = synchronizationService.createSync(Platform.Microinvest);

        SyncLog syncLog = syncLogService.startSync(
                Platform.Microinvest,
                SyncDirection.INBOUND,
                SyncOperation.RETURNS,
                sync,
                batchId
        );

        int processedItems = 0;
        int successfulItems = 0;
        int failedItems = 0;

        try {
            Optional<List<OperationDto>> returnsOpt = fetchOperationsFromMicroinvestApi(STORNO_OPERATION_TYPE, from, to);

            if (returnsOpt.isEmpty() || returnsOpt.get().isEmpty()) {
                log.info("No Microinvest returns found for period {} to {}", from, to);
                syncLogService.completeSync(syncLog.getId(), 0, 0, 0, "No returns found for the specified period");
                return;
            }

            List<OperationDto> returns = returnsOpt.get();
            log.info("Processing {} Microinvest returns", returns.size());

            for (OperationDto operation : returns) {
                processedItems++;
                try {
                    productService.increaseAvailabilityByReturn(operation.getGoodId(), operation.getQuantity());
                    productService.setSync(operation.getGoodId(), sync);
                    successfulItems++;

                    log.debug("Processed return for product {}: {} units", operation.getGoodId(), operation.getQuantity());

                    // Update progress every 10 items
                    if (processedItems % 10 == 0) {
                        syncLogService.updateProgress(
                                syncLog.getId(),
                                processedItems,
                                successfulItems,
                                failedItems,
                                String.format("Processed %d/%d Microinvest returns", processedItems, returns.size())
                        );
                    }

                } catch (Exception e) {
                    failedItems++;
                    log.error("Failed to process Microinvest return for product {}: {}",
                            operation.getGoodId(), e.getMessage(), e);
                }
            }

            String details = String.format("Processed Microinvest returns from %s to %s", from, to);
            syncLogService.completeSync(syncLog.getId(), processedItems, successfulItems, failedItems, details);

            log.info("Microinvest returns processing completed: {} total, {} successful, {} failed",
                    processedItems, successfulItems, failedItems);

        } catch (Exception e) {
            log.error("Failed to process Microinvest returns", e);
            syncLogService.failSync(syncLog.getId(), e.getMessage(), processedItems, successfulItems, failedItems);
        }
    }

    public void processDeliveryToMicroinvestApi(String productId, Integer quantity) {
        boolean success = false;
        String errorMessage = null;

        try {
            OperationDto request = createDeliveryRequest(productId, quantity);
            createMicroinvestApiOperation(List.of(request)).block();
            success = true;
            log.info("Successfully created delivery operation for product {} with quantity {}", productId, quantity);
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("Failed to create delivery operation for product {}: {}", productId, e.getMessage(), e);
        }

        // Log the delivery operation
        syncLogService.logSingleOperation(
                Platform.Microinvest,
                SyncDirection.OUTBOUND,
                SyncOperation.STOCK_UPDATE,
                null,
                success,
                String.format("Delivery operation for product %s, quantity %d", productId, quantity),
                errorMessage
        );
    }

    public void processSaleToMicroinvestApi(String productId, Integer quantity) {
        boolean success = false;
        String errorMessage = null;

        try {
            OperationDto request = createSaleRequest(productId, quantity);
            createMicroinvestApiOperation(List.of(request)).block();
            success = true;
            log.info("Successfully created sale operation for product {} with quantity {}", productId, quantity);
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("Failed to create sale operation for product {}: {}", productId, e.getMessage(), e);
        }

        // Log the sale operation
        syncLogService.logSingleOperation(
                Platform.Microinvest,
                SyncDirection.OUTBOUND,
                SyncOperation.ORDERS,
                null,
                success,
                String.format("Sale operation for product %s, quantity %d", productId, quantity),
                errorMessage
        );
    }

    public List<ItemDto> fetchAllItemsFromMicroinvestApi() {
        String batchId = "microinvest-items-import-" + System.currentTimeMillis();

        SyncLog syncLog = syncLogService.startSync(
                Platform.Microinvest,
                SyncDirection.INBOUND,
                SyncOperation.PRODUCT_IMPORT,
                null,
                batchId
        );

        List<ItemDto> allItems = new ArrayList<>();
        int processedItems = 0;
        int successfulItems = 0;
        int failedItems = 0;

        try {
            String url = "/items";
            int page = 1;
            int pageSize = 500;
            final int[] totalPages = {Integer.MAX_VALUE};

            log.info("Starting Microinvest items import");

            while (page <= totalPages[0]) {
                int currentPage = page;

                try {
                    List<ItemDto> pageResult = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path(url)
                                    .queryParam("page", currentPage)
                                    .queryParam("page_size", pageSize)
                                    .build())
                            .exchangeToMono(response -> {
                                HttpHeaders headers = response.headers().asHttpHeaders();
                                String totalPagesHeader = headers.getFirst("X-TotalPages");

                                if (totalPagesHeader != null) {
                                    try {
                                        totalPages[0] = Integer.parseInt(totalPagesHeader);
                                    } catch (NumberFormatException e) {
                                        log.warn("Invalid X-TotalPages header: {}", totalPagesHeader);
                                        totalPages[0] = 1;
                                    }
                                }

                                return response.bodyToMono(new ParameterizedTypeReference<List<ItemDto>>() {});
                            })
                            .doOnError(e -> log.error("Error fetching items from page {}: {}", currentPage, e.getMessage()))
                            .onErrorReturn(List.of())
                            .block();

                    if (pageResult == null || pageResult.isEmpty()) {
                        break;
                    }

                    // Process items from this page
                    int pageSuccessful = fillAllItemsInDb(pageResult);
                    processedItems += pageResult.size();
                    successfulItems += pageSuccessful;
                    failedItems += (pageResult.size() - pageSuccessful);

                    allItems.addAll(pageResult);

                    log.info("Fetched page {} with {} items", currentPage, pageResult.size());

                    // Update progress
                    syncLogService.updateProgress(
                            syncLog.getId(),
                            processedItems,
                            successfulItems,
                            failedItems,
                            String.format("Imported page %d/%d with %d items", currentPage, totalPages[0], pageResult.size())
                    );

                    page++;

                } catch (Exception e) {
                    log.error("Failed to process page {}: {}", currentPage, e.getMessage(), e);
                    failedItems++;
                    page++;
                }
            }

            String details = String.format("Imported %d pages with %d total items", page - 1, processedItems);
            syncLogService.completeSync(syncLog.getId(), processedItems, successfulItems, failedItems, details);

            log.info("Microinvest items import completed: {} total items, {} successful, {} failed",
                    processedItems, successfulItems, failedItems);

        } catch (Exception e) {
            log.error("Failed to import Microinvest items", e);
            syncLogService.failSync(syncLog.getId(), e.getMessage(), processedItems, successfulItems, failedItems);
        }

        return allItems;
    }

    /**
     * Fetch all store quantities from Microinvest API with pagination
     * @return List of all store DTOs containing stock quantities
     */
    public List<StoreDto> fetchAllStoreQuantitiesFromMicroinvestApi() {
        String batchId = "microinvest-store-import-" + System.currentTimeMillis();

        SyncLog syncLog = syncLogService.startSync(
                Platform.Microinvest,
                SyncDirection.INBOUND,
                SyncOperation.STOCK_UPDATE,
                null,
                batchId
        );

        List<StoreDto> allStoreItems = new ArrayList<>();
        int processedItems = 0;
        int successfulItems = 0;
        int failedItems = 0;

        try {
            String url = "/Store";
            int page = 1;
            int pageSize = 500;
            final int[] totalPages = {Integer.MAX_VALUE};

            log.info("Starting Microinvest store quantities import");

            while (page <= totalPages[0]) {
                int currentPage = page;

                try {
                    List<StoreDto> pageResult = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path(url)
                                    .queryParam("page", currentPage)
                                    .queryParam("page_size", pageSize)
                                    .build())
                            .exchangeToMono(response -> {
                                HttpHeaders headers = response.headers().asHttpHeaders();
                                String totalPagesHeader = headers.getFirst("X-TotalPages");

                                if (totalPagesHeader != null) {
                                    try {
                                        totalPages[0] = Integer.parseInt(totalPagesHeader);
                                    } catch (NumberFormatException e) {
                                        log.warn("Invalid X-TotalPages header: {}", totalPagesHeader);
                                        totalPages[0] = 1;
                                    }
                                }

                                return response.bodyToMono(new ParameterizedTypeReference<List<StoreDto>>() {});
                            })
                            .doOnError(e -> log.error("Error fetching store data from page {}: {}", currentPage, e.getMessage()))
                            .onErrorReturn(List.of())
                            .block();

                    if (pageResult == null || pageResult.isEmpty()) {
                        log.info("No more store data found on page {}, stopping", currentPage);
                        break;
                    }

                    // Filter items with quantity > 0 if needed (optional)
                    List<StoreDto> nonZeroStock = pageResult.stream()
                            .filter(store -> store.getQuantity() != null && store.getQuantity() > 0)
                            .toList();

                    processedItems += pageResult.size();
                    successfulItems += pageResult.size();

                    allStoreItems.addAll(pageResult);

                    log.info("Fetched page {} with {} store items ({} with stock > 0)",
                            currentPage, pageResult.size(), nonZeroStock.size());

                    // Update progress
                    syncLogService.updateProgress(
                            syncLog.getId(),
                            processedItems,
                            successfulItems,
                            failedItems,
                            String.format("Imported page %d/%d with %d store items",
                                    currentPage, totalPages[0], pageResult.size())
                    );

                    page++;

                } catch (Exception e) {
                    log.error("Failed to process store page {}: {}", currentPage, e.getMessage(), e);
                    failedItems++;
                    page++;

                    // Continue to next page even if current page fails
                    if (page > totalPages[0]) {
                        break;
                    }
                }
            }

            String details = String.format("Imported %d pages with %d total store items",
                    page - 1, processedItems);
            syncLogService.completeSync(syncLog.getId(), processedItems, successfulItems, failedItems, details);

            log.info("Microinvest store quantities import completed: {} total items, {} successful, {} failed",
                    processedItems, successfulItems, failedItems);

            // Log summary of non-zero stock items
            long nonZeroStockCount = allStoreItems.stream()
                    .filter(store -> store.getQuantity() != null && store.getQuantity() > 0)
                    .count();

            log.info("Total store items with stock > 0: {}", nonZeroStockCount);

        } catch (Exception e) {
            log.error("Failed to import Microinvest store quantities", e);
            syncLogService.failSync(syncLog.getId(), e.getMessage(), processedItems, successfulItems, failedItems);
        }

        return allStoreItems;
    }

    /**
     * Get store quantities filtered by location
     * @param objectId The location/object ID to filter by
     * @return List of store DTOs for the specified location
     */
    public List<StoreDto> fetchStoreQuantitiesByLocation(Long objectId) {
        log.info("Fetching store quantities for location: {}", objectId);

        try {
            List<StoreDto> storeItems = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Store")
                            .queryParam("object_id", objectId)
                            .queryParam("page_size", 2000) // Larger page size for single location
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<StoreDto>>() {})
                    .doOnNext(result -> log.info("Fetched {} store items for location {}", result.size(), objectId))
                    .doOnError(error -> log.error("Error fetching store data for location {}: {}", objectId, error.getMessage()))
                    .onErrorReturn(List.of())
                    .block();

            if (storeItems != null) {
                long nonZeroStock = storeItems.stream()
                        .filter(store -> store.getQuantity() != null && store.getQuantity() > 0)
                        .count();

                log.info("Location {} has {} total items, {} with stock > 0",
                        objectId, storeItems.size(), nonZeroStock);
            }

            return storeItems != null ? storeItems : List.of();

        } catch (Exception e) {
            log.error("Failed to fetch store quantities for location {}: {}", objectId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Get store quantity for specific product at specific location
     * @param goodId Product ID
     * @param objectId Location ID
     * @return Store DTO or null if not found
     */
    public Optional<StoreDto> fetchStoreQuantityForProduct(String goodId, Long objectId) {
        log.debug("Fetching store quantity for product {} at location {}", goodId, objectId);

        try {
            List<StoreDto> storeItems = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Store")
                            .queryParam("good_id", goodId)
                            .queryParam("object_id", objectId)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<StoreDto>>() {})
                    .doOnError(error -> log.error("Error fetching store data for product {} at location {}: {}",
                            goodId, objectId, error.getMessage()))
                    .onErrorReturn(List.of())
                    .block();

            if (storeItems != null && !storeItems.isEmpty()) {
                StoreDto storeItem = storeItems.get(0);
                log.debug("Found product {} at location {} with quantity {}",
                        goodId, objectId, storeItem.getQuantity());
                return Optional.of(storeItem);
            }

            log.debug("No store data found for product {} at location {}", goodId, objectId);
            return Optional.empty();

        } catch (Exception e) {
            log.error("Failed to fetch store quantity for product {} at location {}: {}",
                    goodId, objectId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Debug method to test Store API and understand the response
     */
    /**
     * Fixed method to debug Store API with proper error handling
     */
    public void debugStoreApiFixed() {
        log.info("=== DEBUGGING STORE API - FIXED VERSION ===");

        try {
            // Test 1: Get raw response as String first
            log.info("Test 1: Getting raw response as String");
            String rawResponse = webClient.get()
                    .uri("/Store?page=1&page_size=10")
                    .exchangeToMono(response -> {
                        log.info("Response status: {}", response.statusCode());
                        HttpHeaders headers = response.headers().asHttpHeaders();
                        log.info("Response headers: {}", headers);

                        return response.bodyToMono(String.class);
                    })
                    .block();

            log.info("Raw response: {}", rawResponse);

            // Check if response is an error object
            if (rawResponse != null && rawResponse.trim().startsWith("{")) {
                log.warn("API returned an object (possibly error) instead of array");

                // Try to parse as error
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> errorObj = mapper.readValue(rawResponse, Map.class);
                    log.error("API Error Response: {}", errorObj);
                } catch (Exception e) {
                    log.error("Could not parse error response: {}", e.getMessage());
                }
            } else if (rawResponse != null && rawResponse.trim().startsWith("[")) {
                log.info("API returned array as expected");
                // Try to parse as StoreDto array
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    List<StoreDto> stores = mapper.readValue(rawResponse,
                            new TypeReference<List<StoreDto>>() {});
                    log.info("Successfully parsed {} store items", stores.size());
                    if (!stores.isEmpty()) {
                        log.info("First store item: {}", stores.get(0));
                    }
                } catch (Exception e) {
                    log.error("Could not parse store array: {}", e.getMessage());
                }
            } else {
                log.warn("Unexpected response format or empty response");
            }

        } catch (Exception e) {
            log.error("Error in debug method: {}", e.getMessage(), e);
        }

        log.info("=== END DEBUGGING ===");
    }

    /**
     * Robust method to fetch store data with proper error handling
     */
    public List<StoreDto> fetchStoreDataRobust() {
        log.info("Fetching store data with robust error handling");

        try {
            return webClient.get()
                    .uri("/Store?page=1&page_size=100")
                    .exchangeToMono(response -> {
                        log.info("Store API response status: {}", response.statusCode());

                        if (!response.statusCode().is2xxSuccessful()) {
                            log.error("Store API returned error status: {}", response.statusCode());
                            return response.bodyToMono(String.class)
                                    .doOnNext(errorBody -> log.error("Error response body: {}", errorBody))
                                    .then(Mono.just(List.<StoreDto>of()));
                        }

                        return response.bodyToMono(String.class)
                                .map(rawResponse -> {
                                    log.debug("Raw response: {}", rawResponse);

                                    if (rawResponse == null || rawResponse.trim().isEmpty()) {
                                        log.warn("Empty response from Store API");
                                        return List.<StoreDto>of();
                                    }

                                    // Check if it's an error response (starts with '{')
                                    if (rawResponse.trim().startsWith("{")) {
                                        log.error("Store API returned error object: {}", rawResponse);
                                        return List.<StoreDto>of();
                                    }

                                    // Try to parse as array
                                    if (rawResponse.trim().startsWith("[")) {
                                        try {
                                            ObjectMapper mapper = new ObjectMapper();
                                            List<StoreDto> stores = mapper.readValue(rawResponse,
                                                    new TypeReference<List<StoreDto>>() {});
                                            log.info("Successfully parsed {} store items", stores.size());
                                            return stores;
                                        } catch (Exception e) {
                                            log.error("Failed to parse store data: {}", e.getMessage());
                                            return List.<StoreDto>of();
                                        }
                                    } else {
                                        log.error("Unexpected response format: {}", rawResponse.substring(0, Math.min(100, rawResponse.length())));
                                        return List.<StoreDto>of();
                                    }
                                });
                    })
                    .doOnError(error -> log.error("Error fetching store data: {}", error.getMessage()))
                    .onErrorReturn(List.of())
                    .block();

        } catch (Exception e) {
            log.error("Exception in fetchStoreDataRobust: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Test with different Store API endpoints to find working one
     */
    public void testStoreEndpoints() {
        log.info("=== TESTING DIFFERENT STORE ENDPOINTS ===");

        String[] testUrls = {
                "/Store",
                "/store",
                "/Store?page=1",
                "/Store?page=1&page_size=10",
                "/Store?object_id=1",
                "/Store?object_id=2",
                "/Store?object_id=10"
        };

        for (String url : testUrls) {
            try {
                log.info("Testing URL: {}", url);

                String response = webClient.get()
                        .uri(url)
                        .exchangeToMono(clientResponse -> {
                            log.info("URL: {} - Status: {}", url, clientResponse.statusCode());
                            return clientResponse.bodyToMono(String.class);
                        })
                        .block();

                if (response != null) {
                    log.info("URL: {} - Response length: {}", url, response.length());
                    log.info("URL: {} - Response starts with: {}", url,
                            response.substring(0, Math.min(50, response.length())));

                    if (response.trim().startsWith("[") && response.contains("object_id")) {
                        log.info("*** URL: {} - SUCCESS! Returns valid array ***", url);
                    }
                } else {
                    log.warn("URL: {} - NULL response", url);
                }

            } catch (Exception e) {
                log.error("URL: {} - Error: {}", url, e.getMessage());
            }
        }

        log.info("=== END TESTING ENDPOINTS ===");
    }

    /**
     * Alternative: Try to fetch store data using Map first, then convert
     */
    public List<StoreDto> fetchStoreUsingMaps() {
        log.info("Fetching store data using Map approach");

        try {
            List<Map<String, Object>> mapResult = webClient.get()
                    .uri("/Store?page=1&page_size=50")
                    .exchangeToMono(response -> {
                        if (!response.statusCode().is2xxSuccessful()) {
                            log.error("Store API error status: {}", response.statusCode());
                            return Mono.just(List.<Map<String, Object>>of());
                        }

                        return response.bodyToMono(String.class)
                                .map(rawResponse -> {
                                    if (rawResponse == null || !rawResponse.trim().startsWith("[")) {
                                        log.error("Invalid response format for Map parsing: {}",
                                                rawResponse != null ? rawResponse.substring(0, Math.min(100, rawResponse.length())) : "null");
                                        return List.<Map<String, Object>>of();
                                    }

                                    try {
                                        ObjectMapper mapper = new ObjectMapper();
                                        return mapper.readValue(rawResponse,
                                                new TypeReference<List<Map<String, Object>>>() {});
                                    } catch (Exception e) {
                                        log.error("Failed to parse as Map list: {}", e.getMessage());
                                        return List.<Map<String, Object>>of();
                                    }
                                });
                    })
                    .block();

            if (mapResult != null && !mapResult.isEmpty()) {
                log.info("Successfully fetched {} store items as Maps", mapResult.size());
                log.info("First item keys: {}", mapResult.get(0).keySet());
                log.info("First item: {}", mapResult.get(0));

                // Convert Maps to StoreDto
                List<StoreDto> stores = mapResult.stream()
                        .map(this::convertMapToStoreDto)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                log.info("Converted {} Maps to StoreDto objects", stores.size());
                return stores;
            }

            return List.of();

        } catch (Exception e) {
            log.error("Error in fetchStoreUsingMaps: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Helper method to convert Map to StoreDto
     */
    private StoreDto convertMapToStoreDto(Map<String, Object> map) {
        try {
            StoreDto store = new StoreDto();

            if (map.get("id") != null) {
                store.setId(Long.valueOf(map.get("id").toString()));
            }

            if (map.get("object_id") != null) {
                store.setObjectId(Long.valueOf(map.get("object_id").toString()));
            }

            if (map.get("good_id") != null) {
                store.setGoodId(map.get("good_id").toString());
            }

            if (map.get("qtty") != null) {
                store.setQuantity(Integer.valueOf(map.get("qtty").toString()));
            }

            if (map.get("price") != null) {
                store.setPrice(new BigDecimal(map.get("price").toString()));
            }

            return store;

        } catch (Exception e) {
            log.error("Failed to convert map to StoreDto: {}", e.getMessage());
            return null;
        }
    }
    private int fillAllItemsInDb(List<ItemDto> items) {
        int successful = 0;

        if (!items.isEmpty()) {
            for (ItemDto item : items) {
                try {
                    ProductRequest productRequest = new ProductRequest();
                    productRequest.setId(String.valueOf(item.getId()));
                    productRequest.setName(item.getName());
                    productRequest.setBasePrice(BigDecimal.valueOf(item.getPriceIn()));
                    productRequest.setMicroinvestPrice(BigDecimal.valueOf(item.getPriceOut2()));
                    productService.createProduct(productRequest);
                    successful++;
                } catch (Exception e) {
                    log.error("Failed to create product for item {}: {}", item.getId(), e.getMessage());
                }
            }
            log.debug("Successfully created {} out of {} products from current batch", successful, items.size());
        } else {
            log.warn("No items found in batch!");
        }

        return successful;
    }

    // Keep existing methods unchanged
    public Optional<List<OperationDto>> fetchOperationsFromMicroinvestApi(Integer operationType, LocalDate fromDate, LocalDate toDate) {
        String url = "/operations";

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(url)
                        .queryParam("operation_type", operationType)
                        .queryParam("date_from", fromDate)
                        .queryParam("date_to", toDate)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<OperationDto>>() {})
                .doOnNext(result -> log.info("Fetched {} operations from Microinvest (type={})", result.size(), operationType))
                .doOnError(error -> log.error("Error fetching Microinvest operations: {}", error.getMessage()))
                .onErrorReturn(List.of())
                .blockOptional();
    }

    private Mono<List<OperationDto>> createMicroinvestApiOperation(List<OperationDto> operations) {
        return webClient.post()
                .uri("/operation")
                .bodyValue(operations)
                .retrieve()
                .onStatus(httpStatusCode -> !httpStatusCode.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class).flatMap(error ->
                                Mono.error(new RuntimeException("Microinvest API error: " + error))
                        )
                )
                .bodyToFlux(OperationDto.class)
                .collectList();
    }

    private OperationDto createDeliveryRequest(String productId, Integer quantity) {
        OperationDto operationDto = new OperationDto();
        operationDto.setOperationType(1);
        operationDto.setGoodId(productId);
        operationDto.setPartnerId(String.valueOf(2));
        operationDto.setObjectId(String.valueOf(2));
        operationDto.setOperatorId(String.valueOf(2));
        operationDto.setQuantity(quantity);
        operationDto.setSign(1);
        operationDto.setDate(LocalDateTime.now());
        operationDto.setNote("Автоматична доставка от Sigmatherm web-server");
        operationDto.setUserId(8L);
        return operationDto;
    }

    private OperationDto createSaleRequest(String productId, Integer quantity) {
        OperationDto operationDto = new OperationDto();
        operationDto.setOperationType(2);
        operationDto.setGoodId(productId);
        operationDto.setPartnerId(String.valueOf(2));
        operationDto.setObjectId(String.valueOf(2));
        operationDto.setOperatorId(String.valueOf(2));
        operationDto.setQuantity(quantity);
        operationDto.setSign(-1);
        operationDto.setDate(LocalDateTime.now());
        operationDto.setNote("Автоматична продажба от Sigmatherm web-server");
        operationDto.setUserId(8L);
        return operationDto;
    }
}