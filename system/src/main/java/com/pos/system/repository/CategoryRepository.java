package com.pos.system.repository;

import com.pos.system.model.catalog.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByBranchId(Long branchId);
    List<Category> findByBranchIdAndIsActive(Long branchId, Boolean isActive);
    List<Category> findByBranchIdAndParentId(Long branchId, Long parentId);
    boolean existsByBranchIdAndName(Long branchId, String name);
    Optional<Category> findByCategoryIdAndBranchId(Long categoryId, Long branchId);
}
