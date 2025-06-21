package com.ludogoriesoft.sigmatherm.dto.magento;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class MagentoProductSalesDto {
    private String id;

    @PositiveOrZero
    private int sales;
}
