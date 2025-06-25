package com.ludogoriesoft.sigmatherm.dto.response;

import com.ludogoriesoft.sigmatherm.entity.Brand;

import com.ludogoriesoft.sigmatherm.entity.Synchronization;
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
