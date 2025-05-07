package com.ludogoriesoft.sigmatherm.dto.emag;

import lombok.Data;

import java.util.List;

@Data
public class EmagProduct {
    private String ext_part_number;
    private String part_number_key;
    private double retained_amount;
    private String sale_price;
    private String created;
    private String modified;
    private String original_price;
    private long id;
    private String product_id;
    private String part_number;
    private String currency;
    private long mkt_id;
    private String name;
    private int quantity;
    private String vat;
    private int status;
    private List<Object> attachments;
    private int initial_qty;
    private int storno_qty;
    private List<Object> recycle_warranties;
    private List<Object> product_voucher_split;
    private List<Object> details;
}
