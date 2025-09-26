package com.ludogoriesoft.sigmatherm.dto.skroutz;

import jakarta.xml.bind.annotation.*;
import lombok.Data;

import java.util.List;

@XmlRootElement(name = "mywebstore")
@XmlAccessorType(XmlAccessType.FIELD)
@Data
public class MyWebStore {

    @XmlElement(name = "created_at")
    private String createdAt;

    @XmlElementWrapper(name = "products")
    @XmlElement(name = "product")
    private List<SkroutzProductDto> products;
}

