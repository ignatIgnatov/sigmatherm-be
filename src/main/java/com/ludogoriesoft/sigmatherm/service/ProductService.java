package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.request.ProductRequest;
import com.ludogoriesoft.sigmatherm.dto.response.ProductResponse;
import com.ludogoriesoft.sigmatherm.entity.Product;
import com.ludogoriesoft.sigmatherm.entity.Supplier;
import com.ludogoriesoft.sigmatherm.entity.Synchronization;
import com.ludogoriesoft.sigmatherm.exception.ObjectExistsException;
import com.ludogoriesoft.sigmatherm.exception.ObjectNotFoundException;
import com.ludogoriesoft.sigmatherm.repository.ProductRepository;
import com.ludogoriesoft.sigmatherm.repository.SupplierRepository;
import java.util.List;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

  private static final Logger LOGGER = Logger.getLogger(ProductService.class.getName());

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

  public void reduceAvailability(String productId, int quantityOrdered) {
    if (existsById(productId)) {
      Product product = findById(productId);
      int newAvailability = product.getAvailability() - quantityOrdered;

      product.setAvailability(newAvailability);
      productRepository.save(product);
      LOGGER.info("Availability reduced for product with id " + productId);
    } else {
      LOGGER.warning("Product with id " + productId + " not found");
    }
  }

  private Product findById(String id) {
    return productRepository
        .findById(id)
        .orElseThrow(() -> new ObjectNotFoundException("Product with id " + id + " not found"));
  }

  private boolean existsById(String id) {
    return productRepository.existsById(id);
  }

  public List<ProductResponse> getAllProducts() {
    return productRepository.findAll().stream()
        .map(p -> modelMapper.map(p, ProductResponse.class))
        .toList();
  }

  private Product createProductInDb(ProductRequest productRequest) {
    Supplier supplier = supplierRepository.findByNameIgnoreCase(productRequest.getSupplierName());
    Product product =
        Product.builder()
            .id(productRequest.getId())
            .name(productRequest.getName())
            .supplier(supplier)
            .basePrice(productRequest.getBasePrice())
            .availability(productRequest.getWarehouseAvailability())
            .build();
    productRepository.save(product);
    return product;
  }

  public void setSync(String id, Synchronization synchronization) {
    if (productRepository.existsById(id)) {
      Product product = findById(id);
      product.setSynchronization(synchronization);
      productRepository.save(product);
    }
  }
}
