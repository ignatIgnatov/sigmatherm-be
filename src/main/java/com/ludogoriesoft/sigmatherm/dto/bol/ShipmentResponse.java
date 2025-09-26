package com.ludogoriesoft.sigmatherm.dto.bol;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class ShipmentResponse {
    private List<Shipment> shipments;

    @Data
    public static class Shipment {
        private String shipmentId;
        private OffsetDateTime shipmentDateTime;
        private List<ShipmentItem> shipmentItems;
    }

    @Data
    public static class ShipmentItem {
        private String orderItemId;
        private String ean;
        private int quantity;
        private ShipmentOffer offer;
    }

    @Data
    public static class ShipmentOffer {
        private String offerId;
        private String reference;
    }
}

