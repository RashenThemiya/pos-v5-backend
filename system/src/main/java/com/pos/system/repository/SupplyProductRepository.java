package com.pos.system.repository;

import com.pos.system.model.supplier.SupplyProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplyProductRepository extends JpaRepository<SupplyProduct, Long> {
    List<SupplyProduct> findBySupplyId(Long supplyId);
    Optional<SupplyProduct> findByInternalBatchBarcode(String internalBatchBarcode);
    boolean existsByInternalBatchBarcode(String internalBatchBarcode);
}