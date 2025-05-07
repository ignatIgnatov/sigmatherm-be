package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrder;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrdersCount;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrdersCountResponse;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagOrdersResponse;
import com.ludogoriesoft.sigmatherm.dto.emag.EmagProduct;
import com.ludogoriesoft.sigmatherm.exception.EmagException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class EmagService {

  private static final String AUTHORIZATION = "Authorization";
  private static final String TOKEN_PREFIX = "Basic ";
  private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
  private static final String READ_PATH = "/read";
  private static final String COUNT_PATH = "/count";

  private static final Logger LOGGER = Logger.getLogger(EmagService.class.getName());

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

  @Scheduled(cron = "0 0 0 * * *")
  public void fetchEmagBgOrders() {
    fetchEmagOrders(emagBgUrl);
    LOGGER.info("EMAG_BG fetched successfully!");
  }

  @Scheduled(cron = "0 10 0 * * *")
  public void fetchEmagRoOrders() {
    fetchEmagOrders(emagRoUrl);
    LOGGER.info("EMAG_RO fetched successfully!");
  }

  @Scheduled(cron = "0 20 0 * * *")
  public void fetchEmagHuOrders() {
    fetchEmagOrders(emagHuUrl);
    LOGGER.info("EMAG_HU fetched successfully!");
  }

  private void fetchEmagOrders(String url) {
    EmagOrdersCountResponse ordersCountResponse = getEmagOrdersCountResponse(url + COUNT_PATH);
    if (ordersCountResponse.isError()) {
      LOGGER.warning(ordersCountResponse.getMessages().getFirst());
      throw new EmagException(ordersCountResponse.getMessages().getFirst());
    }

    EmagOrdersCount ordersCount = ordersCountResponse.getResults();

    for (int i = 1; i <= ordersCount.getNoOfPages(); i++) {
      EmagOrdersResponse response = getEmagOrdersResponse(url + READ_PATH, i);

      if (response.isError()) {
        LOGGER.warning(response.getMessages().getFirst());
        throw new EmagException(response.getMessages().getFirst());
      }

      for (EmagOrder order : response.getResults()) {
        for (EmagProduct product : order.getProducts()) {
          productService.reduceAvailability(product.getProduct_id(), product.getQuantity());
        }
      }
    }
  }

  private EmagOrdersResponse getEmagOrdersResponse(String url, int page) {
    HttpHeaders headers = getHeaders();

    MultiValueMap<String, String> body = getOrdersRequestBody(page);

    HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

    ResponseEntity<EmagOrdersResponse> response =
        restTemplate.postForEntity(url, entity, EmagOrdersResponse.class);
    return response.getBody();
  }

  private EmagOrdersCountResponse getEmagOrdersCountResponse(String url) {
    HttpHeaders headers = getHeaders();
    MultiValueMap<String, String> body = getOrdersRequestBody(1);
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

  private static MultiValueMap<String, String> getOrdersRequestBody(int page) {
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();

    LocalDate today = LocalDate.now();

    LocalDateTime startOfDay = today.atStartOfDay();
    LocalDateTime endOfDay = today.atTime(23, 59, 59);

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    String createdAfter = startOfDay.format(formatter);
    String createdBefore = endOfDay.format(formatter);

    body.add("createdBefore", createdBefore);
    body.add("createdAfter", createdAfter);
    body.add("status", "4");
    body.add("currentPage", String.valueOf(page));
    return body;
  }
}
