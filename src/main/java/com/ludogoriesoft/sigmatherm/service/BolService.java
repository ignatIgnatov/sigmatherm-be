package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.bol.OrderResponse;
import com.ludogoriesoft.sigmatherm.dto.bol.ReturnsResponse;
import com.ludogoriesoft.sigmatherm.dto.bol.ShipmentResponse;
import com.ludogoriesoft.sigmatherm.dto.bol.StockUpdateRequest;
import com.ludogoriesoft.sigmatherm.dto.bol.TokenResponse;
import com.ludogoriesoft.sigmatherm.exception.ObjectNotFoundException;
import com.ludogoriesoft.sigmatherm.model.Product;
import com.ludogoriesoft.sigmatherm.model.SyncLog;
import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.model.enums.Platform;
import com.ludogoriesoft.sigmatherm.model.enums.SyncDirection;
import com.ludogoriesoft.sigmatherm.model.enums.SyncOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BolService {

    @Value("${bol.client.id}")
    private String clientId;

    @Value("${bol.client.secret}")
    private String clientSecret;

    private final ProductService productService;
    private final SynchronizationService synchronizationService;
    private final SyncLogService syncLogService;
    private final WebClient webClient = WebClient.create("https://api.bol.com");

    private static final String ACCEPT_HEADER = "application/vnd.retailer.v10+json";

    public void processStockUpdateToBol(String offerId, int stock) {
        boolean success = false;
        String errorMessage = null;

        try {
            String token = getAccessToken();
            updateSingleStockToBol(token, offerId, stock);
            success = true;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("Failed to update stock for offer {} to BOL", offerId, e);
        }

        syncLogService.logSingleOperation(
                Platform.Bol,
                SyncDirection.OUTBOUND,
                SyncOperation.STOCK_UPDATE,
                null,
                success,
                String.format("Stock update for offer %s to %d", offerId, stock),
                errorMessage
        );
    }

    public List<ShipmentResponse.Shipment> processShipments() {
        String batchId = "bol-shipments-" + System.currentTimeMillis();
        Synchronization synchronization = synchronizationService.createSync(Platform.Bol);

        SyncLog syncLog = syncLogService.startSync(
                Platform.Bol,
                SyncDirection.INBOUND,
                SyncOperation.ORDERS,
                synchronization,
                batchId
        );

        int processedItems = 0;
        int successfulItems = 0;
        int failedItems = 0;

        try {
            String accessToken = obtainAccessToken();
            ShipmentResponse response = fetchShipments(accessToken).block();

            if (response == null || response.getShipments() == null || response.getShipments().isEmpty()) {
                log.info("No shipments found in the response");
                syncLogService.completeSync(syncLog.getId(), 0, 0, 0, "No shipments found");
                return List.of();
            }

            List<ShipmentResponse.Shipment> todayShipments = getTodayShipments(response);
            log.info("Processing {} shipments for today", todayShipments.size());

            if (todayShipments.isEmpty()) {
                syncLogService.completeSync(syncLog.getId(), 0, 0, 0, "No shipments for today");
                return response.getShipments(); // Only for testing
            }

            for (ShipmentResponse.Shipment shipment : todayShipments) {
                try {
                    Thread.sleep(1200);
                    ShipmentResponse.Shipment currentShipment = fetchShipmentById(accessToken, shipment.getShipmentId()).block();

                    if (currentShipment == null || currentShipment.getShipmentItems() == null || currentShipment.getShipmentItems().isEmpty()) {
                        log.info("No shipment items in shipment {}", shipment.getShipmentId());
                        failedItems++;
                        continue;
                    }

                    for (ShipmentResponse.ShipmentItem item : currentShipment.getShipmentItems()) {
                        processedItems++;
                        try {
                            log.info("Item reference: {}", item.getOffer().getReference());
                            log.info("Item offer id: {}", item.getOffer().getOfferId());

                            reduceAvailabilityOfShippedItems(shipment, item.getOffer().getReference(), item.getQuantity(), synchronization);
                            Product product = productService.findProductById(item.getOffer().getReference());
                            updateSingleStockToBol(accessToken, item.getOffer().getOfferId(), product.getStock());

                            successfulItems++;

                            // Update progress periodically
                            if (processedItems % 10 == 0) {
                                syncLogService.updateProgress(syncLog.getId(), processedItems, successfulItems, failedItems,
                                        String.format("Processed %d/%d shipment items", processedItems, todayShipments.size()));
                            }

                        } catch (Exception e) {
                            failedItems++;
                            log.error("Error processing shipment item {}", item.getOffer().getReference(), e);
                        }
                    }

                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                    failedItems++;
                    log.error("Error processing shipment {}", shipment.getShipmentId(), e);
                }
            }

            syncLogService.completeSync(syncLog.getId(), processedItems, successfulItems, failedItems,
                    String.format("Processed %d shipments with %d items total", todayShipments.size(), processedItems));

            log.info(synchronization.getPlatform() + " synchronized successfully!");
            return response.getShipments();

        } catch (Exception e) {
            syncLogService.failSync(syncLog.getId(), e.getMessage(), processedItems, successfulItems, failedItems);
            log.error("Failed to process BOL shipments", e);
            throw new RuntimeException("Failed to process BOL shipments", e);
        }
    }

    public List<ReturnsResponse.Return> processReturns() {
        String batchId = "bol-returns-" + System.currentTimeMillis();
        Synchronization synchronization = synchronizationService.createSync(Platform.Bol);

        SyncLog syncLog = syncLogService.startSync(
                Platform.Bol,
                SyncDirection.INBOUND,
                SyncOperation.RETURNS,
                synchronization,
                batchId
        );

        int processedItems = 0;
        int successfulItems = 0;
        int failedItems = 0;

        try {
            String accessToken = obtainAccessToken();
            ReturnsResponse response = fetchReturns(accessToken).block();

            if (response == null || response.getReturns() == null || response.getReturns().isEmpty()) {
                log.info("No returns found in the response");
                syncLogService.completeSync(syncLog.getId(), 0, 0, 0, "No returns found");
                return List.of();
            }

            List<ReturnsResponse.Return> todayReturns = getTodayReturns(response);
            log.info("Processing {} returns for today", todayReturns.size());

            if (todayReturns.isEmpty()) {
                syncLogService.completeSync(syncLog.getId(), 0, 0, 0, "No returns for today");
                return response.getReturns(); // Only for testing
            }

            for (ReturnsResponse.Return currentReturn : todayReturns) {
                try {
                    Thread.sleep(1200);
                    if (currentReturn.getReturnItems() == null || currentReturn.getReturnItems().isEmpty()) {
                        log.info("No items found in return {}", currentReturn.getReturnId());
                        failedItems++;
                        continue;
                    }

                    for (ReturnsResponse.ReturnItem returnItem : currentReturn.getReturnItems()) {
                        processedItems++;
                        try {
                            OrderResponse order = fetchOrderById(accessToken, returnItem.getOrderId()).block();
                            if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
                                log.info("No orders found in return {}", currentReturn.getReturnId());
                                failedItems++;
                                continue;
                            }

                            boolean returnReceived = checkHandlingResultByReturn(returnItem);
                            if (!returnReceived) {
                                log.info("No RETURN_RECEIVED handling result found in return {}", currentReturn.getReturnId());
                                failedItems++;
                                continue;
                            }

                            reduceAvailabilityOfReturnedItem(currentReturn, returnItem, order, accessToken, synchronization);
                            successfulItems++;

                            // Update progress periodically
                            if (processedItems % 10 == 0) {
                                syncLogService.updateProgress(syncLog.getId(), processedItems, successfulItems, failedItems,
                                        String.format("Processed %d/%d return items", processedItems, todayReturns.size()));
                            }

                        } catch (Exception e) {
                            failedItems++;
                            log.error("Error processing return item", e);
                        }
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                    failedItems++;
                    log.error("Error processing return {}", currentReturn.getReturnId(), e);
                }
            }

            syncLogService.completeSync(syncLog.getId(), processedItems, successfulItems, failedItems,
                    String.format("Processed %d returns with %d items total", todayReturns.size(), processedItems));

            log.info(synchronization.getPlatform() + " synchronized successfully!");
            return response.getReturns();

        } catch (Exception e) {
            syncLogService.failSync(syncLog.getId(), e.getMessage(), processedItems, successfulItems, failedItems);
            log.error("Failed to process BOL returns", e);
            throw new RuntimeException("Failed to process BOL returns", e);
        }
    }

    private String obtainAccessToken() {
        String accessToken = getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            log.error("Failed to obtain access token for BOL API");
            throw new ObjectNotFoundException("No access token found!");
        }
        return accessToken;
    }

    private void updateSingleStockToBol(String token, String offerId, int stock) {
        webClient.put()
                .uri("/retailer/offers/{offerId}/stock", offerId)
                .headers(header -> {
                    header.setBearerAuth(token);
                    header.set(HttpHeaders.ACCEPT, ACCEPT_HEADER);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new StockUpdateRequest(stock, true))
                .retrieve()
                .onStatus(
                        status -> !status.is2xxSuccessful(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> {
                                    log.error("Failed to update stock for offer {}: {} - {}",
                                            offerId, response.statusCode(), body);
                                    return Mono.error(new RuntimeException(
                                            "Stock update failed for offer " + offerId + ": " + body));
                                })
                )
                .bodyToMono(Void.class);
    }

    private Mono<OrderResponse> fetchOrderById(String accessToken, String orderId) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/retailer/orders/" + orderId)
                        .build())
                .headers(header -> {
                    header.setBearerAuth(accessToken);
                    header.set(HttpHeaders.ACCEPT, ACCEPT_HEADER);
                })
                .retrieve()
                .onStatus(
                        status -> !status.is2xxSuccessful(),
                        response -> {
                            log.error("Failed to fetch an order, received status code: {}", response.statusCode());
                            return Mono.error(new RuntimeException("Failed to fetch an order"));
                        }
                )
                .bodyToMono(OrderResponse.class);
    }

    private Mono<ShipmentResponse.Shipment> fetchShipmentById(String accessToken, String shipmentId) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/retailer/shipments/" + shipmentId)
                        .build())
                .headers(header -> {
                    header.setBearerAuth(accessToken);
                    header.set(HttpHeaders.ACCEPT, ACCEPT_HEADER);
                })
                .retrieve()
                .onStatus(
                        status -> !status.is2xxSuccessful(),
                        response -> {
                            log.error("Failed to fetch shipments, received status code: {}", response.statusCode());
                            return Mono.error(new RuntimeException("Failed to fetch shipments"));
                        }
                )
                .bodyToMono(ShipmentResponse.Shipment.class);
    }

    private Mono<ShipmentResponse> fetchShipments(String accessToken) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/retailer/shipments")
                        .queryParam("fulfilment-method", "FBR")
                        .build())
                .headers(header -> {
                    header.setBearerAuth(accessToken);
                    header.set(HttpHeaders.ACCEPT, ACCEPT_HEADER);
                })
                .retrieve()
                .onStatus(
                        status -> !status.is2xxSuccessful(),
                        response -> {
                            log.error("Failed to fetch shipments, received status code: {}", response.statusCode());
                            return Mono.error(new RuntimeException("Failed to fetch shipments"));
                        }
                )
                .bodyToMono(ShipmentResponse.class);
    }

    private Mono<ReturnsResponse> fetchReturns(String accessToken) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/retailer/returns")
//                        .queryParam("handled", "true")
                        .build())
                .headers(header -> {
                    header.setBearerAuth(accessToken);
                    header.set(HttpHeaders.ACCEPT, ACCEPT_HEADER);
                })
                .retrieve()
                .onStatus(
                        status -> !status.is2xxSuccessful(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "Failed to fetch returns: " + response.statusCode() + " - " + body)))
                )
                .bodyToMono(ReturnsResponse.class);
    }

    private static List<ShipmentResponse.Shipment> getTodayShipments(ShipmentResponse response) {
        ZoneId zone = ZoneId.of("Europe/Sofia");
        OffsetDateTime startOfToday = OffsetDateTime.now(zone).minusDays(1)
                .toLocalDate()
                .atStartOfDay()
                .atOffset(OffsetDateTime.now().getOffset());

        OffsetDateTime startOfTomorrow = startOfToday.plusDays(1);

        return response.getShipments().stream()
                .filter(shipment -> {
                    OffsetDateTime shipmentDate = OffsetDateTime.parse(shipment.getShipmentDateTime().toString());
                    return !shipmentDate.isBefore(startOfToday) && shipmentDate.isBefore(startOfTomorrow);
                })
                .toList();
    }

    private static List<ReturnsResponse.Return> getTodayReturns(ReturnsResponse response) {
        ZoneId zone = ZoneId.of("Europe/Sofia");
        OffsetDateTime startOfToday = OffsetDateTime.now(zone).minusDays(1)
                .toLocalDate()
                .atStartOfDay()
                .atOffset(OffsetDateTime.now().getOffset());

        OffsetDateTime startOfTomorrow = startOfToday.plusDays(1);

        return response.getReturns().stream()
                .filter(r -> {
                    OffsetDateTime returnDate = OffsetDateTime.parse(r.getRegistrationDateTime().toString());
                    return !returnDate.isBefore(startOfToday) && returnDate.isBefore(startOfTomorrow);
                })
                .toList();
    }

    private void reduceAvailabilityOfShippedItems(ShipmentResponse.Shipment shipment, String productId, int quantity, Synchronization synchronization) {
        try {
            log.debug("Reducing availability for offer {} by {}", productId, quantity);
            productService.reduceAvailabilityByOrder(productId, quantity);
            productService.setSync(productId, synchronization);
        } catch (Exception e) {
            log.error("Error processing item {} in shipment {}", productId, shipment.getShipmentId(), e);
        }
    }

    private void reduceAvailabilityOfReturnedItem(ReturnsResponse.Return currentReturn, ReturnsResponse.ReturnItem returnItem, OrderResponse order, String accessToken, Synchronization synchronization) {
        for (OrderResponse.OrderItem orderItem : order.getOrderItems()) {
            if (orderItem.getProduct().getEan().equals(returnItem.getEan())) {
                String productId = orderItem.getOffer().getReference();
                try {
                    log.debug("Reducing availability for offer {} by {}", productId, orderItem.getQuantity());
                    productService.increaseAvailabilityByReturn(productId, orderItem.getQuantity());
                    productService.setSync(productId, synchronization);
                    Product product = productService.findProductById(productId);
                    updateSingleStockToBol(accessToken, orderItem.getOffer().getOfferId(), product.getStock());
                } catch (Exception e) {
                    log.error("Error processing item {} in return {}", productId, currentReturn.getReturnId(), e);
                }
            }
        }
    }

    private String getAccessToken() {
        log.debug("Attempting to get BOL access token");
        String auth = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());

        try {
            TokenResponse tokenResponse = WebClient.create("https://login.bol.com")
                    .post()
                    .uri("/token")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                    .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .body(BodyInserters.fromFormData("grant_type", "client_credentials"))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (tokenResponse == null) {
                log.error("Received null token response from BOL auth endpoint");
                return null;
            }

            log.debug("Successfully obtained access token");
            return tokenResponse.getAccessToken();
        } catch (Exception e) {
            log.error("Failed to obtain access token from BOL", e);
            return null;
        }
    }

    private static boolean checkHandlingResultByReturn(ReturnsResponse.ReturnItem returnItem) {
        boolean returnReceived = false;
        if (returnItem.getProcessingResults() == null || returnItem.getProcessingResults().isEmpty()) {
            return false;
        }
        for (ReturnsResponse.ProcessingResult processingResult : returnItem.getProcessingResults()) {
            if (processingResult.getHandlingResult().equals("RETURN_RECEIVED")) {
                returnReceived = true;
            }
        }
        return returnReceived;
    }
}
