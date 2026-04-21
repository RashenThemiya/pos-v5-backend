package com.pos.system.repository;

import com.pos.system.model.stock.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByBranchIdOrderByCreatedAtDesc(Long branchId);
    List<StockMovement> findByBranchIdAndItemIdOrderByCreatedAtDesc(Long branchId, Long itemId);
    List<StockMovement> findByBranchIdAndMovementTypeOrderByCreatedAtDesc(Long branchId, String movementType);
    
}