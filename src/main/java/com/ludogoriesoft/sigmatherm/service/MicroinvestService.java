package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.microinvest.ItemDto;
import com.ludogoriesoft.sigmatherm.dto.microinvest.OperationDto;
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
import java.util.Optional;

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