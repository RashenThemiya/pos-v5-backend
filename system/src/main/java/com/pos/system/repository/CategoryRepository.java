package com.pos.system.repository;

import com.pos.system.model.catalog.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {
    List<Category> findByBranchId(Long branchId);
    List<Category> findByBranchIdAndIsActive(Long branchId, Boolean isActive);
    List<Category> findByBranchIdAndParentId(Long branchId, Long parentId);
    boolean existsByBranchIdAndName(Long branchId, String name);
    Optional<Category> findByCategoryIdAndBranchId(Long categoryId, Long branchId);
}
