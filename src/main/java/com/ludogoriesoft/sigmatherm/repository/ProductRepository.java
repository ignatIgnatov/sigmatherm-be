package com.ludogoriesoft.sigmatherm.repository;

import com.ludogoriesoft.sigmatherm.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    @Query("SELECT p FROM Product p WHERE DATE(p.synchronization.startDate) = CURRENT_DATE")
    List<Product> findAllProductsSynchronizedToday();
}
