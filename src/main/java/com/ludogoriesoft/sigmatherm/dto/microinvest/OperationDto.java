package com.ludogoriesoft.sigmatherm.dto.microinvest;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationDto {
    private Long id;
    private Integer operation_type;
    private Integer document_number;
    private String good_id;
    private String partner_id;
    private String object_id;
    private String operator_id;
    private Integer qtty;
    private Integer sign;
    private Double price_in;
    private Double price_out;
    private Double vat_in;
    private Double vat_out;
    private Double discount;
    private Long currency_id;
    private Double currency_rate;
    private LocalDateTime date;
    private String lot;
    private Long lot_id;
    private String note;
    private Long src_doc_id;
    private Long user_id;
    private LocalDateTime user_real_time;
}
