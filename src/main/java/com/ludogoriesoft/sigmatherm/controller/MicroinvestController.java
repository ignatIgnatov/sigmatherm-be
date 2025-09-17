package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.dto.microinvest.OperationDto;
import com.ludogoriesoft.sigmatherm.dto.microinvest.StoreDto;
import com.ludogoriesoft.sigmatherm.service.MicroinvestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/operations")
public class MicroinvestController {

    private final MicroinvestService microinvestService;

    @GetMapping
    public ResponseEntity<List<OperationDto>> getOperations(
            @RequestParam("type") Integer operationType,
            @RequestParam("from-date") LocalDate fromDate,
            @RequestParam("to-date") LocalDate toDate
    ) {
        List<OperationDto> response = microinvestService
                .fetchOperationsFromMicroinvestApi(operationType, fromDate, toDate)
                .orElse(List.of());
        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/delivery")
    public ResponseEntity<String> createDelivery(@RequestParam("product_id") String productId, @RequestParam("quantity") Integer quantity) {
        microinvestService.processDeliveryToMicroinvestApi(productId, quantity);
        return ResponseEntity.ok().body("Delivery created successfully!");
    }

    @PostMapping("/sale")
    public ResponseEntity<String> createSale(@RequestParam("product_id") String productId, @RequestParam("quantity") Integer quantity) {
        microinvestService.processSaleToMicroinvestApi(productId, quantity);
        return ResponseEntity.ok().body("Sale created successfully!");
    }

    @GetMapping("/store")
    public ResponseEntity<List<StoreDto>> getStore() {
        List<StoreDto> response = microinvestService.fetchAllStoreQuantitiesFromMicroinvestApi();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/store-debug")
    public ResponseEntity<String> getStoreDebug() {
        microinvestService.debugStoreApiFixed();
        return ResponseEntity.ok("End of debug");
    }

//    @GetMapping("/partners")
//    public ResponseEntity<List<PartnerDto>> getAllPartners() {
//        List<PartnerDto> response = microinvestService.fetchAllPartnersFromMicroinvestApi();
//        return ResponseEntity.ok().body(response);
//    }

    @GetMapping("/items")
    public ResponseEntity<String> fillItemsInDb() {
        microinvestService.fetchAllItemsFromMicroinvestApi();
        return ResponseEntity.ok().body("Mission complete!");
    }

    @GetMapping("/orders")
    public ResponseEntity<String> processOrders(@RequestParam("from") LocalDate from, @RequestParam("to") LocalDate to) {
        microinvestService.processMicroinvestOrders(from, to);
        return ResponseEntity.ok().body("Orders complete!");
    }

    @GetMapping("/returns")
    public ResponseEntity<String> processReturns(@RequestParam("from") LocalDate from, @RequestParam("to") LocalDate to) {
        microinvestService.processMicroinvestReturns(from, to);
        return ResponseEntity.ok().body("Returns complete!");
    }
}
