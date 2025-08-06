package com.ludogoriesoft.sigmatherm.dto.microinvest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ItemDto {
    private int id;
    private String code;
    private String name;

    @JsonProperty("name2")
    private String name2;

    @JsonProperty("barcode1")
    private String barcode1;

    @JsonProperty("barcode2")
    private String barcode2;

    @JsonProperty("barcode3")
    private String barcode3;

    @JsonProperty("catalog1")
    private String catalog1;

    @JsonProperty("catalog2")
    private String catalog2;

    @JsonProperty("catalog3")
    private String catalog3;

    @JsonProperty("measure1")
    private String measure1;

    @JsonProperty("measure2")
    private String measure2;

    private double ratio;

    @JsonProperty("price_in")
    private double priceIn;

    @JsonProperty("price_out1")
    private double priceOut1;

    @JsonProperty("price_out2")
    private double priceOut2;

    @JsonProperty("price_out3")
    private double priceOut3;

    @JsonProperty("price_out4")
    private double priceOut4;

    @JsonProperty("price_out5")
    private double priceOut5;
}
