package com.ludogoriesoft.sigmatherm.dto.response;

import com.ludogoriesoft.sigmatherm.entity.Supplier;
import java.math.BigDecimal;

import com.ludogoriesoft.sigmatherm.entity.Synchronization;
import lombok.Data;

@Data
public class ProductResponse {
  private String id;
  private String name;
  private Supplier supplier;
  private BigDecimal basePrice;
  private int stock;
  private Synchronization synchronization;
  private PriceResponse price;
}
