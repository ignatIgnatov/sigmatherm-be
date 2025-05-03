package com.ludogoriesoft.sigmatherm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

  @Id private String id;

  @Column(nullable = false)
  private String name;

  @ManyToOne
  @JoinColumn(name = "supplier_id")
  private Supplier supplier;

  private BigDecimal basePrice;
  private int warehouseAvailability;
  private int shopsAvailability;
}
