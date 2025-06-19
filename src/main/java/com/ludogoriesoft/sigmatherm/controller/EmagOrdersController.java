package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.service.CronJobService;
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

  private final CronJobService cronJobService;

  @GetMapping("/bg")
  public ResponseEntity<String> getEmagBgOrders() {
    cronJobService.fetchEmagBgOrders();
    return new ResponseEntity<>("Emag fetch success!", HttpStatus.OK);
  }

  @GetMapping("/ro")
  public ResponseEntity<String> getEmagRoOrders() {
    cronJobService.fetchEmagRoOrders();
    return new ResponseEntity<>("Emag fetch success!", HttpStatus.OK);
  }

  @GetMapping("/hu")
  public ResponseEntity<String> getEmagHuOrders() {
    cronJobService.fetchEmagHuOrders();
    return new ResponseEntity<>("Emag fetch success!", HttpStatus.OK);
  }

  @GetMapping("all")
  public ResponseEntity<String> getManualEmagOrdersFetch() {
    cronJobService.fetchEmagBgOrders();
    cronJobService.fetchEmagRoOrders();
    cronJobService.fetchEmagHuOrders();
    return new ResponseEntity<>("Emag fetch success!", HttpStatus.OK);
  }
}
