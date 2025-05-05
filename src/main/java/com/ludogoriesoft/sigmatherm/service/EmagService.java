package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.EmagResponse;
import com.ludogoriesoft.sigmatherm.dto.request.ProductRequest;
import com.ludogoriesoft.sigmatherm.exception.EmagException;
import com.ludogoriesoft.sigmatherm.exception.ObjectExistsException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class EmagService {

  private static final String AUTHORIZATION = "Authorization";
  private static final String EMAG_BG_URL =
      "https://marketplace-api.emag.bg/api-3/product_offer/read";
  private static final String EMAG_RO_URL =
      "https://marketplace-api.emag.ro/api-3/product_offer/read";
  private static final String EMAG_HU_URL =
      "https://marketplace-api.emag.hu/api-3/product_offer/read";

  private static final Logger logger = Logger.getLogger(EmagService.class.getName());

  @Value("${emag.api.username}")
  private String username;

  @Value("${emag.api.password}")
  private String password;

  private final RestTemplate restTemplate;
  private final SupplierService supplierService;
  private final ProductService productService;

  @Scheduled(cron = "0 0 0 * * *")
  public void fetchEmagBgProducts() {
    fetchEmagProducts(EMAG_BG_URL);
  }

  @Scheduled(cron = "0 0 0 * * *")
  public void fetchEmagRoProducts() {
    fetchEmagProducts(EMAG_RO_URL);
  }

  @Scheduled(cron = "0 0 0 * * *")
  public void fetchEmagHuProducts() {
    fetchEmagProducts(EMAG_HU_URL);
  }

  private void fetchEmagProducts(String url) {
    EmagResponse response = getEmagResponse(url);

    if (response.isIsError()) {
      logger.warning(response.getMessages().getFirst());
      throw new EmagException(response.getMessages().getFirst());
    }

    for (Map<String, Object> result : response.getResults()) {
      String supplierName = (String) result.get("brand_name");
      if (supplierName != null) {
        supplierService.createSupplierIfNotExists(supplierName, BigDecimal.ZERO);
      }

      Integer availability = getAvailability(result);
      String productId = (String) result.get("part_number");

      if ((productId != null) && productService.existsById(productId)) {
        productService.editAvailability(productId, availability);
      } else {
        ProductRequest productRequest =
            ProductRequest.builder()
                .id(productId)
                .name((String) result.get("name"))
                .supplierName(supplierName)
                .warehouseAvailability(availability)
                .shopsAvailability(availability)
                .build();

        try {
          productService.createProductInDb(productRequest);
        } catch (ObjectExistsException e) {
          logger.warning(e.getMessage());
        }
      }
    }
  }

  private static Integer getAvailability(Map<String, Object> result) {
    List<Map<String, Integer>> availabilities =
        (List<Map<String, Integer>>) result.get("availability");
    Map<String, Integer> availabilityObj = availabilities.getFirst();
    return availabilityObj.get("value");
  }

  private EmagResponse getEmagResponse(String url) {
    HttpHeaders headers = getHeaders();
    HttpEntity<String> entity = new HttpEntity<>(headers);
    ResponseEntity<EmagResponse> response =
        restTemplate.exchange(url, HttpMethod.GET, entity, EmagResponse.class);
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
    return "Basic " + new String(encodedAuth);
  }
}
