package com.ludogoriesoft.sigmatherm.dto;

import com.ludogoriesoft.sigmatherm.entity.enums.EventType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SkroutzOrderWebhook {
    private EventType event_type;
    private Order order;

    @Data
    public static class Order {
        private String code;
        private String created_at;
        private List<OrderLine> order_lines;
    }

    @Data
    public static class OrderLine {
        private String product_name;
        private int quantity;
        private BigDecimal unit_price;
    }
}
