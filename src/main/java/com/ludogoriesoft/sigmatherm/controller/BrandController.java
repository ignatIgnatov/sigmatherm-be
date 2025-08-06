package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.model.Brand;
import com.ludogoriesoft.sigmatherm.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/brands")
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    public ResponseEntity<List<Brand>> getAllBrands() {
        List<Brand> response = brandService.getAllBrands();
        return ResponseEntity.ok().body(response);
    }
}
