package com.pos.system.repository;

import com.pos.system.model.stock.StockCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockCountRepository extends JpaRepository<StockCount, Long> {
    List<StockCount> findByBranchIdOrderByCountDateDesc(Long branchId);
    List<StockCount> findByBranchIdAndStatusOrderByCountDateDesc(Long branchId, String status);
    
}