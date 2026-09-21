package com.pos.system.repository;

import com.pos.system.model.catalog.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long>, JpaSpecificationExecutor<Brand> {
    List<Brand> findByBranchId(Long branchId);
    List<Brand> findByBranchIdAndIsActive(Long branchId, Boolean isActive);
    boolean existsByBranchIdAndName(Long branchId, String name);
    Optional<Brand> findByBrandIdAndBranchId(Long brandId, Long branchId);

    @Query("""
            select distinct b from Brand b
            join BrandCategory bc on bc.brandId = b.brandId
            where b.branchId = :branchId
              and b.isActive = true
              and bc.categoryId = :categoryId
            order by b.name
            """)
    List<Brand> findActiveByBranchIdAndCategoryId(
            @Param("branchId") Long branchId,
            @Param("categoryId") Long categoryId
    );
}
