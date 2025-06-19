package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.request.ProductRequest;
import com.ludogoriesoft.sigmatherm.dto.response.ProductResponse;
import com.ludogoriesoft.sigmatherm.entity.Price;
import com.ludogoriesoft.sigmatherm.entity.ProductEntity;
import com.ludogoriesoft.sigmatherm.entity.Supplier;
import com.ludogoriesoft.sigmatherm.entity.Synchronization;
import com.ludogoriesoft.sigmatherm.exception.ObjectExistsException;
import com.ludogoriesoft.sigmatherm.exception.ObjectNotFoundException;
import com.ludogoriesoft.sigmatherm.repository.ProductRepository;
import com.ludogoriesoft.sigmatherm.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
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

        ProductEntity productEntity = createProductInDb(productRequest);
        return modelMapper.map(productEntity, ProductResponse.class);
    }

    public void reduceAvailabilityByOrder(String productId, int quantityOrdered) {
        ProductEntity productEntity = findProductById(productId);
        if (productEntity != null) {
            int newAvailability = productEntity.getStock() - quantityOrdered;

            if (newAvailability >= 0) {
                productEntity.setStock(newAvailability);
                productRepository.save(productEntity);
                log.info("Availability reduced for product with id " + productId);
            } else {
                //TODO: Not enough stock!
                log.info("Not enough stock for product with ID: " + productId);
            }
        } else {
            log.info("Product with ID: " + productId + " not found");
        }
    }

    public void reduceAvailabilityByReturnedProduct(String productId, int quantityReturned) {
        ProductEntity productEntity = findProductById(productId);
        if (productEntity != null) {
            int newAvailability = productEntity.getStock() + quantityReturned;
            productEntity.setStock(newAvailability);
            productRepository.save(productEntity);
            log.info("Availability reduced for product with id " + productId);

        } else {
            log.info("Product with ID: " + productId + " not found");
        }
    }

    public ProductEntity findProductById(String id) {
        return productRepository
                .findById(id)
                .orElse(null);
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(p -> modelMapper.map(p, ProductResponse.class))
                .toList();
    }

    public void setSync(String id, Synchronization synchronization) {
        ProductEntity productEntity = findProductById(id);
        if (productEntity != null) {
            productEntity.setSynchronization(synchronization);
            productRepository.save(productEntity);
            log.info("Synchronization for product with ID: " + id);
        } else {
            log.info("No product found (with ID: " + id + " ) for synchronization");
        }
    }

    public List<ProductEntity> getAllProductsSynchronizedToday() {
        return productRepository.findAllProductsSynchronizedToday();
    }

    private ProductEntity createProductInDb(ProductRequest productRequest) {
        Supplier supplier = supplierRepository.findByNameIgnoreCase(productRequest.getSupplierName());
        Price price = new Price();
        price.setBasePrice(productRequest.getBasePrice());
        ProductEntity productEntity =
                ProductEntity.builder()
                        .id(productRequest.getId())
                        .name(productRequest.getName())
                        .supplier(supplier)
                        .price(price)
                        .stock(productRequest.getWarehouseAvailability())
                        .build();
        productRepository.save(productEntity);
        log.info("Product with ID: " + productRequest.getId() + " created successfully");
        return productEntity;
    }
}
