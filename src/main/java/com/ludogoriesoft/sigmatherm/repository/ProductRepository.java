package com.ludogoriesoft.sigmatherm.repository;

import com.ludogoriesoft.sigmatherm.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, String> {
    @Query("SELECT p FROM Product p WHERE DATE(p.synchronization.startDate) = CURRENT_DATE")
    List<ProductEntity> findAllProductsSynchronizedToday();
}
