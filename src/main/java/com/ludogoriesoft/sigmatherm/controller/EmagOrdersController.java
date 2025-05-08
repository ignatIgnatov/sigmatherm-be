package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.service.EmagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emag")
@RequiredArgsConstructor
public class EmagOrdersController {

  private final EmagService emagService;

  @GetMapping("/bg")
  public ResponseEntity<String> getEmagBgOrders() {
    emagService.fetchEmagBgOrders();
    return new ResponseEntity<>("Emag fetch success!", HttpStatus.OK);
  }

  @GetMapping("/ro")
  public ResponseEntity<String> getEmagRoOrders() {
    emagService.fetchEmagRoOrders();
    return new ResponseEntity<>("Emag fetch success!", HttpStatus.OK);
  }

  @GetMapping("/hu")
  public ResponseEntity<String> getEmagHuOrders() {
    emagService.fetchEmagHuOrders();
    return new ResponseEntity<>("Emag fetch success!", HttpStatus.OK);
  }

  @GetMapping("all")
  public ResponseEntity<String> getManualEmagOrdersFetch() {
    emagService.fetchEmagBgOrders();
    emagService.fetchEmagRoOrders();
    emagService.fetchEmagHuOrders();
    return new ResponseEntity<>("Emag fetch success!", HttpStatus.OK);
  }
}
