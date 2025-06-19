package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrder;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrdersCount;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrdersCountResponse;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrdersResponse;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagProduct;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagReturnedOrdersResponse;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagReturnedProduct;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagReturnedResult;
import com.ludogoriesoft.sigmatherm.entity.ProductEntity;
import com.ludogoriesoft.sigmatherm.entity.Synchronization;
import com.ludogoriesoft.sigmatherm.entity.enums.Platform;
import com.ludogoriesoft.sigmatherm.exception.EmagException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final SynchronizationService synchronizationService;

    public void uploadActualStockToEmag(String url, String productId, int stock) {
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
            String emagNationality = url.substring(url.length() - 3);
            switch (emagNationality) {
                case ".bg" -> log.info("Emag BG updated successfully!");
                case ".ro" -> log.info("Emag RO updated successfully!");
                case ".hu" -> log.info("Emag HU updated successfully!");
                default -> log.info("Emag updated successfully!");
            }
        }
    }

    public void fetchReturnedEmagOrders(String url, Synchronization lastSync) {

        EmagReturnedOrdersResponse ordersResponse = getEmagReturnedOrdersResponse(url, lastSync);

        for (EmagReturnedResult result : ordersResponse.getResults()) {
            for (EmagReturnedProduct product : result.getProducts()) {
                String productId = product.getProduct_id();
                int quantity = product.getQuantity();
                productService.reduceAvailabilityByReturnedProduct(productId, quantity);
            }
        }
    }


    public void fetchEmagOrders(
            String url, Synchronization synchronization, Synchronization lastSync) throws EmagException {
        EmagOrdersCountResponse ordersCountResponse =
                getEmagOrdersCountResponse(url + COUNT_PATH, lastSync);
        if (ordersCountResponse.isError()) {
            log.warn(ordersCountResponse.getMessages().get(0));
            throw new EmagException(ordersCountResponse.getMessages().get(0));
        }

        EmagOrdersCount ordersCount = ordersCountResponse.getResults();

        for (int i = 1; i <= ordersCount.getNoOfPages(); i++) {
            EmagOrdersResponse response = getEmagOrdersResponse(url + READ_PATH, i, lastSync);

            if (response.isError()) {
                log.warn(response.getMessages().get(0));
                throw new EmagException(response.getMessages().get(0));
            }

            for (EmagOrder order : response.getResults()) {
                for (EmagProduct product : order.getProducts()) {
                    productService.reduceAvailabilityByOrder(product.getProduct_id(), product.getQuantity());
                    productService.setSync(product.getProduct_id(), synchronization);
                }
            }
        }

        synchronizationService.setEndDate(synchronization);
        log.info(synchronization.getPlatform() + " synchronized successfully!");
    }

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

    private static MultiValueMap<String, String> getOrdersRequestBody(
            int page, Synchronization lastSync) {

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();

        ZoneId zone = ZoneId.of("Europe/Sofia");
        LocalDate today = LocalDate.now(zone);

        LocalDateTime startTime = today.atStartOfDay();
        LocalDateTime endTime = today.atTime(23, 59, 59);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

        String createdAfter = startTime.format(formatter);
        if (lastSync != null) {
            createdAfter =
                    lastSync
                            .getEndDate()
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
        LocalDate today = LocalDate.now(zone);

        LocalDateTime startTime = today.atStartOfDay();
        LocalDateTime endTime = today.atTime(23, 59, 59);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

        String createdAfter = startTime.format(formatter);
        if (lastSync != null) {
            createdAfter =
                    lastSync
                            .getEndDate()
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
