package com.ludogoriesoft.sigmatherm.dto.emag;

import lombok.Data;

@Data
public class EmagCustomer {
    private long id;
    private long mkt_id;
    private String name;
    private String company;
    private String gender;
    private String phone_1;
    private String phone_2;
    private String phone_3;
    private String registration_number;
    private String code;
    private String email;
    private String billing_name;
    private String billing_phone;
    private String billing_country;
    private String billing_suburb;
    private String billing_city;
    private String billing_locality_id;
    private String billing_street;
    private String billing_postal_code;
    private String shipping_country;
    private String shipping_suburb;
    private String shipping_city;
    private String shipping_locality_id;
    private String shipping_postal_code;
    private String shipping_contact;
    private String shipping_phone;
    private String created;
    private String modified;
    private String bank;
    private String iban;
    private int legal_entity;
    private Object fax;
    private int is_vat_payer;
    private String liable_person;
    private String shipping_street;
}
