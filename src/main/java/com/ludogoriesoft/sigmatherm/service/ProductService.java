package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.request.ProductRequest;
import com.ludogoriesoft.sigmatherm.dto.response.ProductResponse;
import com.ludogoriesoft.sigmatherm.entity.Price;
import com.ludogoriesoft.sigmatherm.entity.Product;
import com.ludogoriesoft.sigmatherm.entity.Brand;
import com.ludogoriesoft.sigmatherm.entity.Synchronization;
import com.ludogoriesoft.sigmatherm.exception.ObjectExistsException;
import com.ludogoriesoft.sigmatherm.exception.ObjectNotFoundException;
import com.ludogoriesoft.sigmatherm.repository.ProductRepository;
import com.ludogoriesoft.sigmatherm.repository.BrandRepository;
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
    private final BrandRepository brandRepository;
    private final ModelMapper modelMapper;

    public ProductResponse createProduct(ProductRequest productRequest) {
        if (productRepository.existsById(productRequest.getId())) {
            throw new ObjectExistsException(
                    "Product with id " + productRequest.getId() + " already exists");
        }

        if (!brandRepository.existsByNameIgnoreCase(productRequest.getSupplierName())) {
            throw new ObjectNotFoundException(
                    "Supplier with name " + productRequest.getSupplierName() + " not found");
        }

        Product product = createProductInDb(productRequest);
        return modelMapper.map(product, ProductResponse.class);
    }

    public void reduceAvailabilityByOrder(String productId, int quantityOrdered) {
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

    public void increaseAvailabilityByReturn(String productId, int quantityReturned) {
        Product product = findProductById(productId);
        if (product != null) {
            int newAvailability = product.getStock() + quantityReturned;
            product.setStock(newAvailability);
            productRepository.save(product);
            log.info("Availability reduced for product with id " + productId);

        } else {
            log.info("Product with ID: " + productId + " not found");
        }
    }

    public Product findProductById(String id) {
        return productRepository
                .findById(id)
                .orElse(null);
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(p -> modelMapper.map(p, ProductResponse.class))
                .toList();
    }

    public void setProductSynchronization(String productId, Synchronization synchronization) {
        Product product = findProductById(productId);
        if (product != null) {
            product.setSynchronization(synchronization);
            productRepository.save(product);
        }
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

    public List<Product> getAllProductsSynchronizedToday() {
        return productRepository.findAllProductsSynchronizedToday();
    }

    private Product createProductInDb(ProductRequest productRequest) {
        Brand brand = brandRepository.findByNameIgnoreCase(productRequest.getSupplierName());
        Price price = new Price();
        price.setBasePrice(productRequest.getBasePrice());
        Product product =
                Product.builder()
                        .id(productRequest.getId())
                        .name(productRequest.getName())
                        .brand(brand)
                        .price(price)
                        .stock(productRequest.getWarehouseAvailability())
                        .build();
        productRepository.save(product);
        log.info("Product with ID: " + productRequest.getId() + " created successfully");
        return product;
    }
}
