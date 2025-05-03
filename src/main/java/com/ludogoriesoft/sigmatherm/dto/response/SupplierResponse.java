package com.ludogoriesoft.sigmatherm.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class SupplierResponse {
    private UUID id;
    private String name;
    private BigDecimal priceMargin;
}
