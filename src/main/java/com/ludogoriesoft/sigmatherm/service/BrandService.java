package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.entity.Brand;
import com.ludogoriesoft.sigmatherm.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    public Brand createBrandIfNotExists(String name, BigDecimal priceMargin) {
        if (!brandRepository.existsByNameIgnoreCase(name)) {
            Brand brand = Brand.builder()
                    .name(name)
                    .priceMargin(priceMargin)
                    .build();

            brandRepository.save(brand);
            return brand;
        }
        return brandRepository.findByNameIgnoreCase(name);
    }

    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }
}
