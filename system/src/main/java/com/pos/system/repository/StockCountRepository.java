package com.pos.system.repository;

import com.pos.system.model.stock.StockCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockCountRepository extends JpaRepository<StockCount, Long> {
    List<StockCount> findByBranchIdOrderByCountDateDesc(Long branchId);
    Page<StockCount> findByBranchId(Long branchId, Pageable pageable);
    List<StockCount> findByBranchIdAndStatusOrderByCountDateDesc(Long branchId, String status);
    
}
