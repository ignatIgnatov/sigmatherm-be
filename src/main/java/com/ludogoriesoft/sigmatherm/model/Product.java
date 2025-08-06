package com.ludogoriesoft.sigmatherm.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import jakarta.persistence.OneToOne;
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
  @JoinColumn(name = "brand_id")
  private Brand brand;

  @ManyToOne
  @JoinColumn(name = "synchronization_id")
  private Synchronization synchronization;

  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "price_id", referencedColumnName = "id")
  private Price price;

  private int stock;
  private String status = "4";
  private String vatId;
  private String handlingTime = "1";
}
