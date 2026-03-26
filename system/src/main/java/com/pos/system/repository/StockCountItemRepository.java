package com.pos.system.repository;

import com.pos.system.model.stock.StockCountItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockCountItemRepository extends JpaRepository<StockCountItem, Long> {
    List<StockCountItem> findByStockCountId(Long stockCountId);
    void deleteByStockCountId(Long stockCountId);
}
