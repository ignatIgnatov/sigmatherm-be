package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.dto.bol.ReturnsResponse;
import com.ludogoriesoft.sigmatherm.dto.bol.ShipmentResponse;
import com.ludogoriesoft.sigmatherm.model.Product;
import com.ludogoriesoft.sigmatherm.repository.ProductRepository;
import com.ludogoriesoft.sigmatherm.service.BolService;
import com.ludogoriesoft.sigmatherm.service.ExcelService;
import com.ludogoriesoft.sigmatherm.service.ProductService;
import com.ludogoriesoft.sigmatherm.service.SkroutzFeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
public class TestController {

    private final ExcelService excelService;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final SkroutzFeedService skroutzFeedService;
    private final BolService bolService;

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
        skroutzFeedService.processStockUpdateToSkroutz(new File(FEED_PATH), products);

        return ResponseEntity.ok().body("Product updated successfully!");
    }

    @GetMapping("/bol/shipments")
    public ResponseEntity<List<ShipmentResponse.Shipment>> getShipments() {
        List<ShipmentResponse.Shipment> response = bolService.processShipments();
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/bol/returns")
    public ResponseEntity<List<ReturnsResponse.Return>> getReturns() {
        List<ReturnsResponse.Return> response = bolService.processReturns();
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/bol/update-stock")
    public ResponseEntity<String> updateStock(@RequestParam("offerId") String offerId, @RequestParam("stock") int stock) {
        bolService.processStockUpdateToBol(offerId, stock);
        return ResponseEntity.ok().body("Stock updated successfully");
    }
}
