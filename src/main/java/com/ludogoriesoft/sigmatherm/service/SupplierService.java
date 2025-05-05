package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.entity.Supplier;
import com.ludogoriesoft.sigmatherm.repository.SupplierRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupplierService {

  private final SupplierRepository supplierRepository;

  public Supplier createSupplierIfNotExists(String name, BigDecimal priceMargin) {
    if (!supplierRepository.existsByNameIgnoreCase(name)) {
      Supplier supplier = Supplier.builder()
              .name(name)
              .priceMargin(priceMargin)
              .build();

      supplierRepository.save(supplier);
      return supplier;
    }
    return supplierRepository.findByNameIgnoreCase(name);
  }
}
