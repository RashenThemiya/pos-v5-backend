package com.pos.system.repository;

import com.pos.system.model.promotion.PromotionBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PromotionBatchRepository extends JpaRepository<PromotionBatch, Long> {
    List<PromotionBatch> findByPromotionId(Long promotionId);
    Optional<PromotionBatch> findByPromotionIdAndBarcode(Long promotionId, String barcode);
    Optional<PromotionBatch> findByBranchIdAndBarcodeAndIsActiveTrue(Long branchId, String barcode);
    List<PromotionBatch> findByPromotionIdAndIsActiveTrue(Long promotionId);
}
