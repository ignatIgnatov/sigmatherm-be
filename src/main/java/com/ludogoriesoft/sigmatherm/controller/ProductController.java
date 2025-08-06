package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.dto.response.PageResponse;
import com.ludogoriesoft.sigmatherm.dto.response.ProductResponse;
import com.ludogoriesoft.sigmatherm.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> getAllProductsPaginated(
            @PageableDefault(page = 0, size = 500, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String search
    ) {
        List<String> allowedSortFields = List.of("id", "name");
        pageable.getSort().forEach(order -> {
            if (!allowedSortFields.contains(order.getProperty())) {
                throw new IllegalArgumentException("Invalid sort field: " + order.getProperty());
            }
        });

        Page<ProductResponse> response = productService.getAllProductsPaginated(pageable, search);
        return ResponseEntity.ok(PageResponse.from(response));
    }

}
