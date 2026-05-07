package com.pos.system.repository;

import com.pos.system.model.sale.SalesReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesReturnItemRepository extends JpaRepository<SalesReturnItem, Long> {
    List<SalesReturnItem> findByReturnId(Long returnId);
}
