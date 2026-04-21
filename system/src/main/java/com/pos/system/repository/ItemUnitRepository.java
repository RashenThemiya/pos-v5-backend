package com.pos.system.repository;

import com.pos.system.model.catalog.ItemUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemUnitRepository extends JpaRepository<ItemUnit, Long> {
    List<ItemUnit> findByItemId(Long itemId);
    Optional<ItemUnit> findByItemIdAndIsBaseUnitTrue(Long itemId);
    Optional<ItemUnit> findByBranchIdAndBarcode(Long branchId, String barcode);
    boolean existsByBranchIdAndBarcode(Long branchId, String barcode);
    boolean existsByItemIdAndUnitName(Long itemId, String unitName);
    boolean existsByItemIdAndUnitNameAndUnitIdNot(Long itemId, String unitName, Long unitId);
        Optional<ItemUnit> findByUnitIdAndIsActiveTrue(Long unitId);

    Optional<ItemUnit> findByItemIdAndUnitIdAndIsActiveTrue(Long itemId, Long unitId);

    Optional<ItemUnit> findByItemIdAndIsBaseUnitTrueAndIsActiveTrue(Long itemId);

    List<ItemUnit> findByItemIdAndIsActiveTrue(Long itemId);

    Optional<ItemUnit> findByBranchIdAndBarcodeAndIsActiveTrue(Long branchId, String barcode);
}