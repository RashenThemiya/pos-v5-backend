package com.pos.system.repository;

import com.pos.system.model.stock.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByBranchIdOrderByCreatedAtDesc(Long branchId);
    Page<StockMovement> findByBranchId(Long branchId, Pageable pageable);
    List<StockMovement> findByBranchIdAndItemIdOrderByCreatedAtDesc(Long branchId, Long itemId);
    List<StockMovement> findByBranchIdAndMovementTypeOrderByCreatedAtDesc(Long branchId, String movementType);
    boolean existsByVariantId(Long variantId);
    
}
