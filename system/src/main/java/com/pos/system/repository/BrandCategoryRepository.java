package com.pos.system.repository;

import com.pos.system.model.catalog.BrandCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandCategoryRepository extends JpaRepository<BrandCategory, Long> {
    List<BrandCategory> findByBrandId(Long brandId);
    List<BrandCategory> findByBrandIdIn(Collection<Long> brandIds);
    List<BrandCategory> findByBranchIdAndCategoryId(Long branchId, Long categoryId);
    Optional<BrandCategory> findByBrandIdAndCategoryId(Long brandId, Long categoryId);
    boolean existsByBrandIdAndCategoryId(Long brandId, Long categoryId);
    void deleteByBrandId(Long brandId);
}
