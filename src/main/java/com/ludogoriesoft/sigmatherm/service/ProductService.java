package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.request.ProductRequest;
import com.ludogoriesoft.sigmatherm.dto.response.ProductResponse;
import com.ludogoriesoft.sigmatherm.model.Price;
import com.ludogoriesoft.sigmatherm.model.Product;
import com.ludogoriesoft.sigmatherm.model.Synchronization;
import com.ludogoriesoft.sigmatherm.repository.BrandRepository;
import com.ludogoriesoft.sigmatherm.repository.PriceRepository;
import com.ludogoriesoft.sigmatherm.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;
    private final BrandRepository brandRepository;
    private final ModelMapper modelMapper;

    public ProductResponse createProduct(ProductRequest productRequest) {
        if (productRepository.existsById(productRequest.getId())) {
            log.warn("Product with id {} already exists", productRequest.getId());
            return new ProductResponse();
//            throw new ObjectExistsException(
//                    "Product with id " + productRequest.getId() + " already exists");
        }

//        if (!brandRepository.existsByNameIgnoreCase(productRequest.getSupplierName())) {
//            throw new ObjectNotFoundException(
//                    "Supplier with name " + productRequest.getSupplierName() + " not found");
//        }

        Product product = createProductInDb(productRequest);
        return modelMapper.map(product, ProductResponse.class);
    }

    public void reduceAvailabilityByOrder(String productId, int quantityOrdered) {
        Product product = findProductById(productId);
        if (product != null) {
            log.info("Product availability: {}, Order quantity: {}", product.getStock(), quantityOrdered);
            int newAvailability = product.getStock() - Math.abs(quantityOrdered);
            log.info("New availability: {}", newAvailability);
            product.setStock(newAvailability);
            productRepository.save(product);
            if (newAvailability >= 0) {
                log.info("Availability reduced for product with id " + productId);
            } else {
                log.warn("Not enough stock for product with ID: " + productId);
            }
        } else {
            log.warn("Product with ID: " + productId + " not found");
        }
    }

    public void increaseAvailabilityByReturn(String productId, int quantityReturned) {
        Product product = findProductById(productId);
        if (product != null) {
            log.info("Product availability: {}, Order quantity: {}", product.getStock(), quantityReturned);
            int newAvailability = product.getStock() + Math.abs(quantityReturned);
            log.info("New availability: {}", newAvailability);
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
//        Brand brand = brandRepository.findByNameIgnoreCase(productRequest.getSupplierName());
        Price price = new Price();
        price.setBasePrice(productRequest.getBasePrice());
        price.setMicroInvestPrice(productRequest.getMicroinvestPrice());
        priceRepository.save(price);
        Product product =
                Product.builder()
                        .id(productRequest.getId())
                        .name(productRequest.getName())
                        .handlingTime("1")
                        .status("4")
//                        .brand(brand)
                        .price(price)
                        .stock(1000)
                        .build();
        productRepository.save(product);
//        log.info("Product with ID: " + productRequest.getId() + " created successfully");
        return product;
    }

    public Product saveProduct(Product product) {
        return productRepository.save(product);
    }

    public Page<ProductResponse> getAllProductsPaginated(Pageable pageable, String search) {
        if (search == null || search.isBlank()) {
            return productRepository.findAll(pageable).map(product -> modelMapper.map(product, ProductResponse.class));
        } else {
            return productRepository.findByNameOrId(search, pageable)
                    .map(product -> modelMapper.map(product, ProductResponse.class));
        }
    }
}
