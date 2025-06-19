package com.ludogoriesoft.sigmatherm.dto.skroutz;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.*;
import lombok.Data;

@XmlAccessorType(XmlAccessType.FIELD)
@Data
public class SkroutzProductDto {
    @XmlElement(name = "id")
    private String id;

    @XmlElement(name = "name")
    private String name;

    @XmlElement(name = "link")
    private String link;

    @XmlElement(name = "image")
    private String image;

    @XmlElement(name = "additionalimage2")
    private String additionalImage2;

    @XmlElement(name = "additionalimage3")
    private String additionalImage3;

    @XmlElement(name = "additionalimage4")
    private String additionalImage4;

    @XmlElement(name = "additionalimage5")
    private String additionalImage5;

    @XmlElement(name = "category")
    private String category;

    @XmlElement(name = "price_with_vat")
    private Double priceWithVat;

    @XmlElement(name = "vat")
    private Double vat;

    @XmlElement(name = "manufacturer")
    private String manufacturer;

    @XmlElement(name = "mpn")
    private String mpn;

    @XmlElement(name = "ean")
    private String ean;

    @XmlElement(name = "availability")
    private String availability;

    @XmlElement(name = "weight")
    private String weight;

    @XmlElement(name = "color")
    private String color;

    @XmlElement(name = "description")
    private String description;

    @XmlElement(name = "quantity")
    private Integer quantity;
}
