package com.pos.system.repository;

import com.pos.system.model.stock.StockBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {
    List<StockBatch> findByBranchId(Long branchId);
    List<StockBatch> findByBranchIdAndItemId(Long branchId, Long itemId);
    Optional<StockBatch> findByInternalBatchBarcode(String internalBatchBarcode);
    boolean existsByInternalBatchBarcode(String internalBatchBarcode);
        Optional<StockBatch> findByBranchIdAndInternalBatchBarcode(Long branchId, String internalBatchBarcode);
List<StockBatch> findByBranchIdOrderByCreatedAtDesc(Long branchId);
List<StockBatch> findByBranchIdAndItemIdOrderByCreatedAtDesc(Long branchId, Long itemId);

@Query("SELECT b FROM StockBatch b WHERE b.branchId = :branchId AND " +
        "(LOWER(b.internalBatchBarcode) LIKE LOWER(CONCAT('%', :query, '%')) " +
        "OR LOWER(b.batchNo) LIKE LOWER(CONCAT('%', :query, '%')) " +
        "OR LOWER(b.supplierBatchBarcode) LIKE LOWER(CONCAT('%', :query, '%')))")
List<StockBatch> searchByBranchAndBatchText(Long branchId, String query);
}
