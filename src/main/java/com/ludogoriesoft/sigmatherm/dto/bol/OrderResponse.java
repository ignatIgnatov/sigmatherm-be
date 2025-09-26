package com.ludogoriesoft.sigmatherm.dto.bol;

import lombok.Data;

import java.util.List;

@Data
public class OrderResponse {
    private String orderId;
    private List<OrderItem> orderItems;

    @Data
    public static class OrderItem {
        private Offer offer;
        private Product product;
        private int quantity;
    }

    @Data
    public static class Product {
        private String ean;
        private String title;
    }

    @Data
    public static class Offer {
        private String offerId;
        private String reference;
    }
}

