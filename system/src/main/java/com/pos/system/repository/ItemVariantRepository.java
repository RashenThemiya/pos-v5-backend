package com.pos.system.repository;

import com.pos.system.model.catalog.ItemVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemVariantRepository extends JpaRepository<ItemVariant, Long> {
    List<ItemVariant> findByItemIdOrderByVariantIdAsc(Long itemId);
    Optional<ItemVariant> findByVariantIdAndItemId(Long variantId, Long itemId);
    Optional<ItemVariant> findByBranchIdAndSku(Long branchId, String sku);
    boolean existsByBranchIdAndSku(Long branchId, String sku);
    boolean existsByBranchIdAndSkuAndVariantIdNot(Long branchId, String sku, Long variantId);
    void deleteByItemId(Long itemId);
}
