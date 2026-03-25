package com.pos.system.repository;

import com.pos.system.model.catalog.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    List<Brand> findByBranchId(Long branchId);
    List<Brand> findByBranchIdAndIsActive(Long branchId, Boolean isActive);
    boolean existsByBranchIdAndName(Long branchId, String name);
    Optional<Brand> findByBrandIdAndBranchId(Long brandId, Long branchId);
}
