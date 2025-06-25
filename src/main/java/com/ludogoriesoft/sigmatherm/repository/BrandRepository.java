package com.ludogoriesoft.sigmatherm.repository;

import com.ludogoriesoft.sigmatherm.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BrandRepository extends JpaRepository<Brand, UUID> {
    boolean existsByNameIgnoreCase(String name);

    Brand findByNameIgnoreCase(String name);
}
