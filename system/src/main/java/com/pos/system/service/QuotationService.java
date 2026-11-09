package com.pos.system.service;

import com.pos.system.dto.sale.QuotationRequest;
import com.pos.system.dto.sale.QuotationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface QuotationService {
    QuotationResponse create(QuotationRequest request);
    QuotationResponse update(Long quotationId, QuotationRequest request);
    QuotationResponse getById(Long quotationId);
    QuotationResponse getByNumber(String quotationNo);
    Page<QuotationResponse> list(Long branchId, String status, Long customerId, Pageable pageable);
    QuotationResponse updateStatus(Long quotationId, String status);
}
