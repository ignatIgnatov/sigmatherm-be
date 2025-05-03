package com.ludogoriesoft.sigmatherm.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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

  @Value("${emag.api.username}")
  private String username;

  @Value("${emag.api.password}")
  private String password;

  private final RestTemplate restTemplate;

  @Scheduled(cron = "0 0 0 * * *")
  public void fetchEmagBgProducts() {
    HttpHeaders headers = getHeaders();
    HttpEntity<String> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange(EMAG_BG_URL, HttpMethod.GET, entity, String.class);
    System.out.println("Response: " + response.getBody());
  }

  @Scheduled(cron = "0 0 0 * * *")
  public void fetchEmagRoProducts() {
    HttpHeaders headers = getHeaders();
    HttpEntity<String> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange(EMAG_RO_URL, HttpMethod.GET, entity, String.class);
  }

  @Scheduled(cron = "0 0 0 * * *")
  public void fetchEmagHunProducts() {
    HttpHeaders headers = getHeaders();
    HttpEntity<String> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange(EMAG_HU_URL, HttpMethod.GET, entity, String.class);
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
