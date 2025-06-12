package com.ludogoriesoft.sigmatherm.dto.emag;

import lombok.Data;

@Data
public class EmagReturnedProduct {
    private long id;
    private long product_emag_id;
    private String product_id;
    private int quantity;
    private String product_name;
    private String diagnostic;
    private String retained_amount;
    private int return_reason;
    private String observations;
}
