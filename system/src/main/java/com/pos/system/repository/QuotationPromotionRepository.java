package com.pos.system.repository;

import com.pos.system.model.sale.QuotationPromotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationPromotionRepository extends JpaRepository<QuotationPromotion, Long> {
    List<QuotationPromotion> findByQuotationIdOrderByQuotationPromotionId(Long quotationId);
    void deleteByQuotationId(Long quotationId);
}
