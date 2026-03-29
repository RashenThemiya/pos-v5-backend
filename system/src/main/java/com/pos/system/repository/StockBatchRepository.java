package com.pos.system.repository;

import com.pos.system.model.stock.StockBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {
    List<StockBatch> findByBranchId(Long branchId);
    List<StockBatch> findByBranchIdAndItemId(Long branchId, Long itemId);
    Optional<StockBatch> findByInternalBatchBarcode(String internalBatchBarcode);
    boolean existsByInternalBatchBarcode(String internalBatchBarcode);
}
