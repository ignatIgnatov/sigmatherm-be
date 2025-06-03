package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrder;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrdersCount;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrdersCountResponse;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrdersResponse;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagProduct;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagReturnedOrdersResponse;
import com.ludogoriesoft.sigmatherm.dto.emag.ResultDto;
import com.ludogoriesoft.sigmatherm.dto.emag.ReturnedProductDto;
import com.ludogoriesoft.sigmatherm.entity.Synchronization;
import com.ludogoriesoft.sigmatherm.entity.enums.Platform;
import com.ludogoriesoft.sigmatherm.exception.EmagException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmagService {

    private static final String AUTHORIZATION = "Authorization";
    private static final String TOKEN_PREFIX = "Basic ";
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String READ_PATH = "/read";
    private static final String COUNT_PATH = "/count";
    private static final String ORDER_URL = "/api-3/order";
    private static final String RETURNED_ORDER_URL = "/api-3/rma/read";

    @Value("${emag.api.username}")
    private String username;

    @Value("${emag.api.password}")
    private String password;

    @Value("${emag.api.bg-url}")
    private String emagBgUrl;

    @Value("${emag.api.ro-url}")
    private String emagRoUrl;

    @Value("${emag.api.hu-url}")
    private String emagHuUrl;

    private final RestTemplate restTemplate;
    private final ProductService productService;
    private final SynchronizationService synchronizationService;

    @Scheduled(cron = "0 0 0 * * *")
    public void fetchEmagBgOrders() {
        Synchronization lastSync = synchronizationService.getLastSyncByPlatform(Platform.eMagBg);
        Synchronization currentSync = synchronizationService.createSync(Platform.eMagBg);
        fetchEmagOrders(emagBgUrl + ORDER_URL, currentSync, lastSync);
        fetchReturnedEmagOrders(emagBgUrl + RETURNED_ORDER_URL, lastSync);
    }

    @Scheduled(cron = "0 2 0 * * *")
    public void fetchEmagRoOrders() {
        Synchronization lastSync = synchronizationService.getLastSyncByPlatform(Platform.eMagRo);
        Synchronization currentSync = synchronizationService.createSync(Platform.eMagRo);
        fetchEmagOrders(emagRoUrl + ORDER_URL, currentSync, lastSync);
        fetchReturnedEmagOrders(emagRoUrl + RETURNED_ORDER_URL, lastSync);
    }

    @Scheduled(cron = "0 4 0 * * *")
    public void fetchEmagHuOrders() {
        Synchronization lastSync = synchronizationService.getLastSyncByPlatform(Platform.eMagHu);
        Synchronization currentSync = synchronizationService.createSync(Platform.eMagHu);
        fetchEmagOrders(emagHuUrl + ORDER_URL, currentSync, lastSync);
        fetchReturnedEmagOrders(emagHuUrl + RETURNED_ORDER_URL, lastSync);
    }

    private void uploadExcelOfferToEmag(String baseUrl) throws IOException {
        String uploadUrl = baseUrl + "/product-offer/save";
        File file = new File("/app/offers/offer.xlsx");

        if (!file.exists()) {
            throw new FileNotFoundException("Excel offer file not found.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set(AUTHORIZATION, createBasicAuthHeader());

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(uploadUrl, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Offer Excel file uploaded successfully to eMAG.");
        } else {
            log.error("Failed to upload offer file to eMAG: " + response.getStatusCode());
            throw new IOException("Upload failed with status: " + response.getStatusCode());
        }
    }

    private void fetchReturnedEmagOrders(String url, Synchronization lastSync) {

        EmagReturnedOrdersResponse ordersResponse = getEmagReturnedOrdersResponse(url, lastSync);

        for (ResultDto result : ordersResponse.getResults()) {
            for (ReturnedProductDto product : result.getProducts()) {
                String productId = product.getProduct_id();
                int quantity = product.getQuantity();
                productService.reduceAvailabilityByReturnedProduct(productId, quantity);
            }
        }
    }


    private void fetchEmagOrders(
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
