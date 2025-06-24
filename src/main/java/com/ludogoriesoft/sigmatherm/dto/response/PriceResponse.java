package com.ludogoriesoft.sigmatherm.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PriceResponse {
    private UUID id;
    private BigDecimal basePrice;
    private BigDecimal eMagBgSalePrice;
    private BigDecimal eMagRoSalePrice;
    private BigDecimal eMagHuSalePrice;
    private BigDecimal skroutzSalePrice;
    private BigDecimal bolSalePrice;
    private BigDecimal magentoSalePrice;
}
