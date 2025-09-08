package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.microinvest.ItemDto;
import com.ludogoriesoft.sigmatherm.dto.microinvest.OperationDto;
import com.ludogoriesoft.sigmatherm.dto.request.ProductRequest;
import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
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
    private final ProductService productService;
    private final BrandService brandService;
    private static final Integer SALE_OPERATION_TYPE = 2;
    private static final Integer STORNO_OPERATION_TYPE = 34;

    public MicroinvestService(@Value("${microinvest.api.url}") String baseUrl, SynchronizationService synchronizationService, ProductService productService, BrandService brandService) {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .baseUrl(baseUrl)
                .build();
        this.synchronizationService = synchronizationService;
        this.productService = productService;
        this.brandService = brandService;
    }

    public void processMicroinvestOrders(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of("Europe/Sofia");
        LocalDate today = LocalDate.now(zone).minusDays(1);
        fetchOperationsFromMicroinvestApi(SALE_OPERATION_TYPE, from, to).ifPresent(orders -> {
            if (!orders.isEmpty()) {
                Synchronization sync = synchronizationService.createSync(Platform.Microinvest);
                for (OperationDto order : orders) {
                    productService.reduceAvailabilityByOrder(order.getGoodId(), order.getQuantity());
                    productService.setSync(order.getGoodId(), sync);
                }
            }
        });
    }

    public void processMicroinvestReturns(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of("Europe/Sofia");
        LocalDate today = LocalDate.now(zone).minusDays(1);
        fetchOperationsFromMicroinvestApi(STORNO_OPERATION_TYPE, from, to).ifPresent(returns -> {
            if (!returns.isEmpty()) {
                Synchronization sync = synchronizationService.createSync(Platform.Microinvest);
                for (OperationDto operation : returns) {
                    productService.increaseAvailabilityByReturn(operation.getGoodId(), operation.getQuantity());
                    productService.setSync(operation.getGoodId(), sync);
                }
            }
        });
    }

    public void processDeliveryToMicroinvestApi(String productId, Integer quantity) {
        OperationDto request = createDeliveryRequest(productId, quantity);
        createMicroinvestApiOperation(List.of(request));
    }

    public void processSaleToMicroinvestApi(String productId, Integer quantity) {
        OperationDto request = createSaleRequest(productId, quantity);
        createMicroinvestApiOperation(List.of(request));
    }

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
                .bodyToMono(new ParameterizedTypeReference<List<OperationDto>>() {
                })
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

    public List<ItemDto> fetchAllItemsFromMicroinvestApi() {
        String url = "/items";
        int page = 1;
        int pageSize = 500;

        List<ItemDto> allItems = new ArrayList<>();
        final int[] totalPages = {Integer.MAX_VALUE};

        while (page <= totalPages[0]) {
            int currentPage = page;

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

                        return response.bodyToMono(new ParameterizedTypeReference<List<ItemDto>>() {
                        });
                    })
                    .doOnError(e -> log.error("Error fetching items from page {}: {}", currentPage, e.getMessage()))
                    .onErrorReturn(List.of())
                    .block();

            if (pageResult == null || pageResult.isEmpty()) {
                break;
            }

            fillAllItemsInDb(pageResult);

            log.info("Fetched page {} with {} items", currentPage, pageResult.size());
            allItems.addAll(pageResult);
            page++;
        }

        log.info("Fetched total of {} items", allItems.size());
        return allItems;
    }

    private void fillAllItemsInDb(List<ItemDto> items) {
        if (!items.isEmpty()) {
            for (ItemDto item : items) {
                ProductRequest productRequest = new ProductRequest();
                productRequest.setId(String.valueOf(item.getId()));
                productRequest.setName(item.getName());
                productRequest.setBasePrice(BigDecimal.valueOf(item.getPriceIn()));
                productRequest.setMicroinvestPrice(BigDecimal.valueOf(item.getPriceOut2()));
                productService.createProduct(productRequest);
            }
            log.info("Successfuly created group of products");
        } else {
            log.warn("No items found!");
        }
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
