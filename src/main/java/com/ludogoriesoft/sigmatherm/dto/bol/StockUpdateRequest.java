package com.ludogoriesoft.sigmatherm.dto.bol;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StockUpdateRequest {
    private int amount;
    private boolean managedByRetailer;
}
