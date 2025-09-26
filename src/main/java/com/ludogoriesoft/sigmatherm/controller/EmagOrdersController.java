package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.service.CronJobService;
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

  private final CronJobService cronJobService;

  @GetMapping("/bg")
  public ResponseEntity<String> getEmagBgOrders() {
    cronJobService.fetchEmagBgData();
    return new ResponseEntity<>("Emag fetch success!", HttpStatus.OK);
  }

  @GetMapping("/ro")
  public ResponseEntity<String> getEmagRoOrders() {
    cronJobService.fetchEmagRoData();
    return new ResponseEntity<>("Emag fetch success!", HttpStatus.OK);
  }

  @GetMapping("/hu")
  public ResponseEntity<String> getEmagHuOrders() {
    cronJobService.fetchEmagHuData();
    return new ResponseEntity<>("Emag fetch success!", HttpStatus.OK);
  }

  @GetMapping("all")
  public ResponseEntity<String> getManualEmagOrdersFetch() {
    cronJobService.fetchEmagBgData();
    cronJobService.fetchEmagRoData();
    cronJobService.fetchEmagHuData();
    return new ResponseEntity<>("Emag fetch success!", HttpStatus.OK);
  }
}
