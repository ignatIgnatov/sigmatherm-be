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

        Product product = createProductInDb(productRequest);
        return modelMapper.map(product, ProductResponse.class);
    }

    public void reduceAvailability(String productId, int quantityOrdered) {
        Product product = findProductById(productId);
        if (product != null) {
            int newAvailability = product.getStock() - quantityOrdered;

            if (newAvailability >= 0) {
                product.setStock(newAvailability);
                productRepository.save(product);
                log.info("Availability reduced for product with id " + productId);
            } else {
                //TODO: Not enough stock!
                log.info("Not enough stock for product with ID: " + productId);
            }
        } else {
            log.info("Product with ID: " + productId + " not found");
        }
    }

    private Product findProductById(String id) {
        return productRepository
                .findById(id)
                .orElse(null);
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
                        .stock(productRequest.getWarehouseAvailability())
                        .build();
        productRepository.save(product);
        log.info("Product with ID: " + productRequest.getId() + " created successfully");
        return product;
    }

    public void setSync(String id, Synchronization synchronization) {
        Product product = findProductById(id);
        if (product != null) {
            product.setSynchronization(synchronization);
            productRepository.save(product);
            log.info("Synchronization for product with ID: " + id);
        } else {
            log.info("No product found (with ID: " + id + " ) for synchronization");
        }
    }
}
