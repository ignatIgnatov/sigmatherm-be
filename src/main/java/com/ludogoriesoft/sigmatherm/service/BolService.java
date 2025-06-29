package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.bol.OrderResponse;
import com.ludogoriesoft.sigmatherm.dto.bol.ReturnsResponse;
import com.ludogoriesoft.sigmatherm.dto.bol.ShipmentResponse;
import com.ludogoriesoft.sigmatherm.dto.bol.StockUpdateRequest;
import com.ludogoriesoft.sigmatherm.dto.bol.TokenResponse;
import com.ludogoriesoft.sigmatherm.entity.Product;
import com.ludogoriesoft.sigmatherm.entity.enums.Platform;
import com.ludogoriesoft.sigmatherm.exception.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BolService {

    private static final String CLIENT_ID = "6f15af67-9412-4318-9813-b7ae097ea75f";
    private static final String CLIENT_SECRET = "6Y(zhhmH5A7vGgg0PTyOYeCtq(vxK3OPyJNcsrSQehXk1lzO(xh!mDDz!xufH0cG";

    private final ProductService productService;
    private final SynchronizationService synchronizationService;
    private final WebClient BOL_WEB_CLIENT = WebClient.create("https://api.bol.com");

    public List<ShipmentResponse.Shipment> processShipments() {
        log.info("Starting to process BOL shipments");

        String accessToken = getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            log.error("Failed to obtain access token for BOL API");
            throw new ObjectNotFoundException("No access token found!");
        }

        ShipmentResponse response = fetchShipments(accessToken).block();

        if (response == null || response.getShipments() == null || response.getShipments().isEmpty()) {
            log.info("No shipments found in the response");
            return List.of();
        }

        List<ShipmentResponse.Shipment> filteredShipments = getTodayShipments(response);

        log.info("Processing {} shipments for today", filteredShipments.size());
        if (filteredShipments.isEmpty()) {
            log.info("Finished processing 0 BOL orders");
            return filteredShipments;
        }

        synchronizationService.createSync(Platform.Bol);
        for (ShipmentResponse.Shipment shipment : filteredShipments) {
            try {
                ShipmentResponse.Shipment currentShipment = fetchShipmentById(accessToken, shipment.getShipmentId()).block();
                if (currentShipment == null || currentShipment.getShipmentItems() == null || currentShipment.getShipmentItems().isEmpty()) {
                    log.info("No shipment items in shipment {}", shipment.getShipmentId());
                    return List.of();
                }

                for (ShipmentResponse.ShipmentItem item : currentShipment.getShipmentItems()) {
                    String productId = item.getOffer().getReference();
                    reduceAvailability(shipment, item, productId);
                }

            } catch (Exception e) {
                log.error("Error processing shipment {}", shipment.getShipmentId(), e);
            }
        }

        return response.getShipments();
    }

    public List<ReturnsResponse.Return> processReturns() {
        log.info("Starting to process BOL returns");

        String accessToken = getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            log.error("Failed to obtain access token for BOL API");
            throw new ObjectNotFoundException("No access token found!");
        }

        ReturnsResponse response = fetchReturns(accessToken).block();

        if (response == null || response.getReturns() == null || response.getReturns().isEmpty()) {
            log.info("No returns found in the response");
            return List.of();
        }

        List<ReturnsResponse.Return> todayReturns = getTodayReturns(response);
        log.info("Found {} today returns", todayReturns.size());

        if (todayReturns.isEmpty()) {
            log.info("Found 0 today returns");
            return List.of();
        }

        for (ReturnsResponse.Return currentReturn : todayReturns) {
            if (currentReturn.getReturnItems() == null || currentReturn.getReturnItems().isEmpty()) {
                log.info("No returns found in this response");
                return List.of();
            }

            for (ReturnsResponse.ReturnItem returnItem : currentReturn.getReturnItems()) {
                OrderResponse order = fetchOrderById(accessToken, returnItem.getOrderId()).block();

                if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
                    log.info("No orders found in return {}", currentReturn.getReturnId());
                    return List.of();
                }

                reduceAvailabilityOfReturnedItem(currentReturn, returnItem, order);
            }
        }

        return response.getReturns();
    }

    public Mono<Void> pushStocksToBol(List<Product> products) {
        if (products == null || products.isEmpty()) {
            log.info("No products to update");
            return Mono.empty();
        }

        return getAccessTokenMono()
                .flatMap(token -> {
                    log.info("Starting stock update for {} products", products.size());

                    return Flux.fromIterable(products)
                            .index()
                            .delayElements(Duration.ofMillis(200))
                            .flatMap(tuple -> {
                                long index = tuple.getT1() + 1;
                                Product product = tuple.getT2();

                                if (product.getId() == null || product.getId().isEmpty()) {
                                    log.warn("[{}/{}] Skipping product {} - no bolOfferId",
                                            index, products.size(), product.getId());
                                    return Mono.empty();
                                }

                                log.debug("[{}/{}] Updating stock for offer {} to {}",
                                        index, products.size(), product.getId(), product.getStock());

                                return updateSingleStock(token, product.getId(), product.getStock())
                                        .onErrorResume(e -> {
                                            log.error("[{}/{}] Failed to update offer {}: {}",
                                                    index, products.size(), product.getId(), e.getMessage());
                                            return Mono.empty();
                                        });
                            })
                            .then();
                })
                .doOnSuccess(v -> log.info("Completed stock update for {} products", products.size()))
                .doOnError(e -> log.error("Stock update failed", e));
    }

    private Mono<Void> updateSingleStock(String token, String offerId, int stock) {
        return BOL_WEB_CLIENT.put()
                .uri("/retailer/offers/{offerId}/stock", offerId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, "application/vnd.retailer.v10+json")
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

    private Mono<String> getAccessTokenMono() {
        return Mono.fromCallable(this::getAccessToken)
                .flatMap(token -> {
                    if (token == null || token.isEmpty()) {
                        return Mono.error(new ObjectNotFoundException("No access token found"));
                    }
                    return Mono.just(token);
                });
    }

    private Mono<OrderResponse> fetchOrderById(String accessToken, String orderId) {
        return BOL_WEB_CLIENT
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/retailer/orders/" + orderId)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.retailer.v10+json")
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
        return BOL_WEB_CLIENT
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/retailer/shipments/" + shipmentId)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.retailer.v10+json")
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
        return BOL_WEB_CLIENT
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/retailer/shipments")
                        .queryParam("fulfilment-method", "FBR")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.retailer.v10+json")
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
        OffsetDateTime todayStart = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
        String todayStartStr = todayStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return BOL_WEB_CLIENT
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/retailer/returns")
                        .queryParam("handled", "true")
                        .queryParam("registration-date-time-from", todayStartStr)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.retailer.v10+json")
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
        OffsetDateTime startOfToday = OffsetDateTime.now()
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
        OffsetDateTime startOfToday = OffsetDateTime.now()
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

    private void reduceAvailability(ShipmentResponse.Shipment shipment, ShipmentResponse.ShipmentItem item, String productId) {
        try {
            log.debug("Reducing availability for offer {} by {}", productId, item.getQuantity());
            productService.reduceAvailabilityByOrder(item.getOrderItemId(), item.getQuantity());
        } catch (Exception e) {
            log.error("Error processing item {} in shipment {}", productId, shipment.getShipmentId(), e);
        }
    }

    private void reduceAvailabilityOfReturnedItem(ReturnsResponse.Return currentReturn, ReturnsResponse.ReturnItem returnItem, OrderResponse order) {
        for (OrderResponse.OrderItem orderItem : order.getOrderItems()) {
            if (orderItem.getProduct().getEan().equals(returnItem.getEan())) {
                String productId = orderItem.getOffer().getReference();
                try {
                    log.debug("Reducing availability for offer {} by {}", productId, orderItem.getQuantity());
                    productService.increaseAvailabilityByReturn(productId, orderItem.getQuantity());
                } catch (Exception e) {
                    log.error("Error processing item {} in return {}", productId, currentReturn.getReturnId(), e);
                }
            }
        }
    }

    private String getAccessToken() {
        log.debug("Attempting to get BOL access token");
        String auth = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());

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
}
