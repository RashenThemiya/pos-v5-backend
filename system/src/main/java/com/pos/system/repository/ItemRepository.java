package com.pos.system.repository;

import com.pos.system.model.catalog.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {
    List<Item> findByBranchId(Long branchId);
    List<Item> findByBranchIdAndIsActive(Long branchId, Boolean isActive);
    List<Item> findByBranchIdAndCategoryId(Long branchId, Long categoryId);
    List<Item> findByBranchIdAndBrandId(Long branchId, Long brandId);
    Optional<Item> findByBranchIdAndSku(Long branchId, String sku);
    boolean existsByBranchIdAndSku(Long branchId, String sku);
    Optional<Item> findByItemIdAndBranchId(Long itemId, Long branchId);
        Optional<Item> findByItemIdAndIsActiveTrue(Long itemId);
}
