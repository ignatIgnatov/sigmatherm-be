package com.ludogoriesoft.sigmatherm.dto.microinvest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StoreDto {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("object_id")
    private Long objectId;

    @JsonProperty("good_id")
    private String goodId;

    @JsonProperty("qtty")
    private Integer quantity;

    @JsonProperty("price")
    private BigDecimal price;
}