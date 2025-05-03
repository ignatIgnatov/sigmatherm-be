package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.response.SupplierResponse;
import com.ludogoriesoft.sigmatherm.entity.Supplier;
import com.ludogoriesoft.sigmatherm.exception.ObjectExistsException;
import com.ludogoriesoft.sigmatherm.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SupplierService {

  private final SupplierRepository supplierRepository;
  private final ModelMapper modelMapper;

  public SupplierResponse createSupplier(String name, BigDecimal priceMargin) {
    if (supplierRepository.existsByNameIgnoreCase(name)) {
      throw new ObjectExistsException("Supplier with name " + name + " already exists");
    }
    Supplier supplier = Supplier.builder()
            .name(name)
            .priceMargin(priceMargin)
            .build();

    supplierRepository.save(supplier);
    return modelMapper.map(supplier, SupplierResponse.class);
  }
}
