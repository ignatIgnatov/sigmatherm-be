package com.ludogoriesoft.sigmatherm.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Data
public class Price {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(mappedBy = "price")
    private Product product;

    private BigDecimal basePrice;

    private BigDecimal microInvestPrice;
    private BigDecimal eMagBgSalePrice;
    private BigDecimal eMagRoSalePrice;
    private BigDecimal eMagHuSalePrice;
    private BigDecimal skroutzSalePrice;
    private BigDecimal bolSalePrice;
    private BigDecimal magentoSalePrice;
}
