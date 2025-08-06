package com.ludogoriesoft.sigmatherm.dto.microinvest;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PartnerDto {
    private String company;
    private BigDecimal discount;
    private String type;
}
