package com.ludogoriesoft.sigmatherm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ludogoriesoft.sigmatherm.model.enums.EventType;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class SkroutzOrderWebhook {
    private EventType event_type;
    private Order order;

    @Data
    public static class Order {
        private String code;
        private String state;
        private String created_at;

        @JsonProperty("line_items")
        private List<LineItem> line_items;
    }

    @Data
    public static class LineItem {
        @JsonProperty("id")
        private String id;
        private String product_name;
        private int quantity;
        private BigDecimal unit_price;
        private String npm;
    }
}
