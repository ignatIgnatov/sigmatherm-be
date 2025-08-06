package com.ludogoriesoft.sigmatherm.dto.microinvest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationDto {
    private Long id;
    @JsonProperty("operation_type")
    private Integer operationType;

    @JsonProperty("good_id")
    private String goodId;

    @JsonProperty("partner_id")
    private String partnerId;

    @JsonProperty("object_id")
    private String objectId;

    @JsonProperty("operator_id")
    private String operatorId;

    @JsonProperty("qtty")
    private Integer quantity;

    private Integer sign;
    private LocalDateTime date;
    private String note;

    @JsonProperty("user_id")
    private Long userId;
}
