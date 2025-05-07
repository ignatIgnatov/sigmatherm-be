package com.ludogoriesoft.sigmatherm.dto.emag;

import lombok.Data;

import java.util.List;

@Data
public class EmagOrder {
    private long id;
    private String vendor_name;
    private int type;
    private Integer parent_id;
    private String date;
    private String payment_mode;
    private String detailed_payment_method;
    private String delivery_mode;
    private String observation;
    private String modified;
    private int status;
    private int payment_status;
    private EmagCustomer customer;
    private List<EmagProduct> products;
    private double shipping_tax;
    private List<Object> shipping_tax_voucher_split;
    private List<Object> vouchers;
    private List<Object> proforms;
    private List<Object> attachments;
    private Object cashed_co;
    private int cashed_cod;
    private Object cancellation_request;
    private int has_editable_products;
    private String refunded_amount;
    private int is_complete;
    private Object reason_cancellation;
    private Object refund_status;
    private String maximum_date_for_shipment;
    private int late_shipment;
    private List<EmagFlag> flags;
    private int emag_club;
    private String finalization_date;
    private Object enforced_vendor_courier_accounts;
    private EmagDetails details;
    private int weekend_delivery;
    private int payment_mode_id;
}
