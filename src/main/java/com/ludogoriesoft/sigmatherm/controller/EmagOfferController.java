package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.service.EmagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emag")
public class EmagOfferController {

  private final EmagService emagService;

  public EmagOfferController(EmagService emagService) {
    this.emagService = emagService;
  }

  @GetMapping("/bg")
  public ResponseEntity<String> getEmagBgOffer() {
    emagService.fetchEmagBgProducts();
    return new ResponseEntity<>("Success", HttpStatus.OK);
  }
}
