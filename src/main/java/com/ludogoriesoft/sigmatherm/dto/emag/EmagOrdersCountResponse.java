package com.ludogoriesoft.sigmatherm.dto.emag;

import lombok.Data;

import java.util.List;

@Data
public class EmagOrdersCountResponse {
    private boolean isError;
    private List<String> messages;
    private List<Object> errors;
    private EmagOrdersCount results;
}
