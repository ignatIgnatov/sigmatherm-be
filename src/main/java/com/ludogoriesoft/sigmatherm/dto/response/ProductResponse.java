package com.ludogoriesoft.sigmatherm.dto.response;

import com.ludogoriesoft.sigmatherm.model.Brand;

import com.ludogoriesoft.sigmatherm.model.Synchronization;
import lombok.Data;

@Data
public class ProductResponse {
  private String id;
  private String name;
  private Brand brand;
  private int stock;
  private Synchronization synchronization;
  private PriceResponse price;
}
