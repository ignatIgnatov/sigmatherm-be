package com.ludogoriesoft.sigmatherm.dto.bol;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class ReturnsResponse {
    private List<Return> returns;

    @Data
    public static class ReturnItem {
        private String rmaId;
        private String orderId;
        private String ean;
        private int expectedQuantity;
        private boolean handled;
        private List<ProcessingResult> processingResults;
    }

    @Data
    public static class Return {
        private String returnId;
        private OffsetDateTime registrationDateTime;
        private List<ReturnItem> returnItems;
    }

    @Data
    public static class ProcessingResult {
        private int quantity;
        private String handlingResult;
        private OffsetDateTime processingDateTime;
    }
}


