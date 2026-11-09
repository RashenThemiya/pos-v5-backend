package com.pos.system.repository;

import com.pos.system.model.sale.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationItemRepository extends JpaRepository<QuotationItem, Long> {
    List<QuotationItem> findByQuotationIdOrderByQuotationItemId(Long quotationId);
    void deleteByQuotationId(Long quotationId);
}
