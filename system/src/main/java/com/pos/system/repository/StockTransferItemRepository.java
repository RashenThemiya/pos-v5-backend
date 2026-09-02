package com.pos.system.repository;

import com.pos.system.model.stock.StockTransferItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransferItemRepository extends JpaRepository<StockTransferItem, Long> {
    List<StockTransferItem> findByTransferId(Long transferId);
    boolean existsByVariantId(Long variantId);
}
