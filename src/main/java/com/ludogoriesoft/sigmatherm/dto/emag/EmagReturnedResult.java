package com.ludogoriesoft.sigmatherm.dto.emag;

import lombok.Data;

import java.util.List;

@Data
public class EmagReturnedResult {
    private int is_full_fbe;
    private long emag_id;
    private String return_parent_id;
    private String order_id;
    private int type;
    private int is_club;
    private int is_fast;
    private String customer_name;
    private String customer_company;
    private String customer_phone;
    private String pickup_country;
    private String pickup_suburb;
    private String pickup_city;
    private String pickup_address;
    private String pickup_zipcode;
    private Integer pickup_locality_id;
    private int pickup_method;
    private String customer_account_iban;
    private String customer_account_bank;
    private String customer_account_beneficiary;
    private String replacement_product_emag_id;
    private String replacement_product_id;
    private String replacement_product_name;
    private Integer replacement_product_quantity;
    private String observations;
    private int request_status;
    private int return_type;
    private int return_reason;
    private String date;
    private List<EmagReturnedProduct> products;
    private Object extra_info;
    private String return_tax_value;
    private String swap;
    private int return_address_id;
    private String country;
    private String address_type;
    private String return_address_snapshot;
    private List<Object> awbs;
    private List<Object> status_history;
    private List<Object> request_history;
    private List<Object> locker;
}
