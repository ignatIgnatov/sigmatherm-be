package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.entity.Product;
import com.ludogoriesoft.sigmatherm.repository.ProductRepository;
import com.ludogoriesoft.sigmatherm.service.ExcelService;
import com.ludogoriesoft.sigmatherm.service.ProductService;
import com.ludogoriesoft.sigmatherm.service.SkroutzFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
public class DummyController {

    private final ExcelService excelService;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final SkroutzFeedService skroutzFeedService;

    private static final String OFFER_PATH = "/app/offers/offer.xlsx";
    private static final String FEED_PATH = "/app/feeds/skroutz_feed.xml";

    @PostMapping("/create-offer")
    public ResponseEntity<String> createExcelOffer() {
        List<Product> productEntities = productRepository.findAll();
        try {
            excelService.createExcelOffer(productEntities);
            return new ResponseEntity<>("Offer created!", HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>("Error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadOffer() {
        File file = new File(OFFER_PATH);

        if (!file.exists()) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=offer.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @PostMapping("/skroutz-change-stock")
    public ResponseEntity<?> changeStock(@RequestParam("productId") String productId) throws Exception {
        Product product = productService.findProductById(productId);

        List<Product> products = new ArrayList<>();
        products.add(product);
        skroutzFeedService.updateFeed(new File(FEED_PATH), products);

        return ResponseEntity.ok().body("Product updated successfully!");
    }
}
