package com.pos.system.repository;

import com.pos.system.model.catalog.ItemTax;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemTaxRepository extends JpaRepository<ItemTax, Long> {
    List<ItemTax> findByItemId(Long itemId);
    List<ItemTax> findByBranchId(Long branchId);
    Optional<ItemTax> findByItemIdAndTaxId(Long itemId, Long taxId);
    boolean existsByItemIdAndTaxId(Long itemId, Long taxId);
    void deleteByItemIdAndTaxId(Long itemId, Long taxId);
}
