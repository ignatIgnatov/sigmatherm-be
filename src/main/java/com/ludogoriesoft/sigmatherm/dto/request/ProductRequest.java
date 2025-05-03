package com.ludogoriesoft.sigmatherm.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ProductRequest {
    @NotBlank(message = "Product ID is required")
    private String id;

    @NotBlank(message = "Product name is required")
    private String name;

    @NotBlank(message = "Supplier name is required")
    private String supplierName;

    private BigDecimal basePrice;
    private int warehouseAvailability;
    private int shopsAvailability;
}
