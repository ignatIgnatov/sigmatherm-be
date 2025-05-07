package com.ludogoriesoft.sigmatherm.dto.emag;

import java.util.List;

import lombok.Data;

@Data
public class EmagOrdersResponse {
    private boolean isError;
    private List<String> messages;
    private List<Object> errors;
    private List<EmagOrder> results;
}

