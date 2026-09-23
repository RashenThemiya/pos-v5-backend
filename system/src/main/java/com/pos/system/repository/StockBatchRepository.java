package com.pos.system.repository;

import com.pos.system.model.stock.StockBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {
    List<StockBatch> findByBranchId(Long branchId);
    List<StockBatch> findByBranchIdAndItemId(Long branchId, Long itemId);
    List<StockBatch> findByBranchIdAndItemIdAndVariantId(Long branchId, Long itemId, Long variantId);
    Optional<StockBatch> findByInternalBatchBarcode(String internalBatchBarcode);
    boolean existsByInternalBatchBarcode(String internalBatchBarcode);
    boolean existsByVariantId(Long variantId);
        Optional<StockBatch> findByBranchIdAndInternalBatchBarcode(Long branchId, String internalBatchBarcode);
List<StockBatch> findByBranchIdOrderByCreatedAtDesc(Long branchId);
Page<StockBatch> findByBranchId(Long branchId, Pageable pageable);
Page<StockBatch> findByBranchIdAndItemId(Long branchId, Long itemId, Pageable pageable);
List<StockBatch> findByBranchIdAndItemIdOrderByCreatedAtDesc(Long branchId, Long itemId);
List<StockBatch> findByBranchIdAndItemIdAndVariantIdOrderByCreatedAtDesc(Long branchId, Long itemId, Long variantId);

@Query("SELECT b FROM StockBatch b WHERE b.branchId = :branchId AND " +
        "(LOWER(b.internalBatchBarcode) LIKE LOWER(CONCAT('%', :query, '%')) " +
        "OR LOWER(b.batchNo) LIKE LOWER(CONCAT('%', :query, '%')) " +
        "OR LOWER(b.supplierBatchBarcode) LIKE LOWER(CONCAT('%', :query, '%')))")
List<StockBatch> searchByBranchAndBatchText(@Param("branchId") Long branchId, @Param("query") String query);

@Query("SELECT b FROM StockBatch b WHERE b.branchId = :branchId AND " +
        "(LOWER(b.internalBatchBarcode) LIKE LOWER(CONCAT('%', :query, '%')) " +
        "OR LOWER(b.batchNo) LIKE LOWER(CONCAT('%', :query, '%')) " +
        "OR LOWER(b.supplierBatchBarcode) LIKE LOWER(CONCAT('%', :query, '%')))")
Page<StockBatch> searchByBranchAndBatchText(@Param("branchId") Long branchId,
                                            @Param("query") String query,
                                            Pageable pageable);

@Query("SELECT b FROM StockBatch b WHERE b.branchId = :branchId AND b.itemId = :itemId AND " +
        "(LOWER(b.internalBatchBarcode) LIKE LOWER(CONCAT('%', :query, '%')) " +
        "OR LOWER(b.batchNo) LIKE LOWER(CONCAT('%', :query, '%')) " +
        "OR LOWER(b.supplierBatchBarcode) LIKE LOWER(CONCAT('%', :query, '%')))")
Page<StockBatch> searchByBranchAndItemAndBatchText(@Param("branchId") Long branchId,
                                                   @Param("itemId") Long itemId,
                                                   @Param("query") String query,
                                                   Pageable pageable);
}
