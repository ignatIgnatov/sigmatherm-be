package com.ludogoriesoft.sigmatherm.repository;

import com.ludogoriesoft.sigmatherm.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    @Query(value = "SELECT p.* FROM product p " +
            "JOIN synchronization s ON p.synchronization_id = s.id " +
            "WHERE DATE(s.read_date) = CURRENT_DATE",
            nativeQuery = true)
    List<Product> findAllProductsSynchronizedToday();

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("""
    SELECT p FROM Product p 
    WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :term, '%')) 
       OR LOWER(p.id) LIKE LOWER(CONCAT('%', :term, '%')) 
""")
    Page<Product> findByNameOrId(@Param("term") String term, Pageable pageable);
}
