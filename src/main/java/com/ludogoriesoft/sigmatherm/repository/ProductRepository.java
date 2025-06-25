package com.ludogoriesoft.sigmatherm.repository;

import com.ludogoriesoft.sigmatherm.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    @Query(value = "SELECT p.* FROM product p " +
            "JOIN synchronization s ON p.synchronization_id = s.id " +
            "WHERE DATE(s.read_date) = CURRENT_DATE",
            nativeQuery = true)
    List<Product> findAllProductsSynchronizedToday();
}
