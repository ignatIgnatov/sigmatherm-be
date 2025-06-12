package com.ludogoriesoft.sigmatherm.dto.emag;

import lombok.Data;

import java.util.List;

@Data
public class EmagReturnedOrdersResponse {
    private boolean isError;
    private List<String> messages;
    private List<String> errors;
    private List<EmagReturnedResult> results;
}
