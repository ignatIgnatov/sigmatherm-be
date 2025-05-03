package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.request.ProductRequest;
import com.ludogoriesoft.sigmatherm.dto.response.ProductResponse;
import com.ludogoriesoft.sigmatherm.entity.Product;
import com.ludogoriesoft.sigmatherm.entity.Supplier;
import com.ludogoriesoft.sigmatherm.exception.ObjectExistsException;
import com.ludogoriesoft.sigmatherm.exception.ObjectNotFoundException;
import com.ludogoriesoft.sigmatherm.repository.ProductRepository;
import com.ludogoriesoft.sigmatherm.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;
  private final SupplierRepository supplierRepository;
  private final ModelMapper modelMapper;

  public ProductResponse createProduct(ProductRequest productRequest) {
    if (productRepository.existsById(productRequest.getId())) {
      throw new ObjectExistsException(
          "Product with id " + productRequest.getId() + " already exists");
    }

    if (!supplierRepository.existsByNameIgnoreCase(productRequest.getSupplierName())) {
      throw new ObjectNotFoundException(
          "Supplier with name " + productRequest.getSupplierName() + " not found");
    }

    Product product = createProductInDb(productRequest);
    return modelMapper.map(product, ProductResponse.class);
  }

  public ProductResponse editAvailability(String id, int availabilityRequest) {
    Product product = findById(id);
    product.setWarehouseAvailability(availabilityRequest);
    product.setShopsAvailability(availabilityRequest);
    productRepository.save(product);
    return modelMapper.map(product, ProductResponse.class);
  }

  private Product findById(String id) {
    return productRepository
        .findById(id)
        .orElseThrow(() -> new ObjectNotFoundException("Product with id " + id + " not found"));
  }

  private Product createProductInDb(ProductRequest productRequest) {
    Supplier supplier = supplierRepository.findByNameIgnoreCase(productRequest.getSupplierName());
    Product product =
        Product.builder()
            .id(productRequest.getId())
            .name(productRequest.getName())
            .supplier(supplier)
            .basePrice(productRequest.getBasePrice())
            .warehouseAvailability(productRequest.getWarehouseAvailability())
            .shopsAvailability(productRequest.getShopsAvailability())
            .build();
    productRepository.save(product);
    return product;
  }
}
