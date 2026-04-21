package com.pos.system.repository;

import com.pos.system.model.stock.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    List<Stock> findByBranchId(Long branchId);
    Optional<Stock> findByBranchIdAndItemId(Long branchId, Long itemId);
    boolean existsByBranchIdAndItemId(Long branchId, Long itemId);
    List<Stock> findByBranchIdOrderByLastUpdatedDesc(Long branchId);

}
