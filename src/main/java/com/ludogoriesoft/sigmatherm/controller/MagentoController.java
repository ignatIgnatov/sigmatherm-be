package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.dto.magento.MagentoProductResponseDto;
import com.ludogoriesoft.sigmatherm.dto.magento.MagentoProductSalesDto;
import com.ludogoriesoft.sigmatherm.service.MagentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/magento")
@RequiredArgsConstructor
public class MagentoController {

    private final MagentoService magentoService;

    @GetMapping
    public ResponseEntity<List<MagentoProductResponseDto>> getAllProducts() {
        List<MagentoProductResponseDto> response = magentoService.getAllSyncProducts();
        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/sales")
    public ResponseEntity<String> receiveProductSales(@RequestBody List<MagentoProductSalesDto> products) {
        return magentoService.receiveProductSales(products);
    }
}
