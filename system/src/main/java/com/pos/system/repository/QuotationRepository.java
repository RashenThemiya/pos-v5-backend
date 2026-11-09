package com.pos.system.repository;

import com.pos.system.model.sale.Quotation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    Optional<Quotation> findByQuotationNo(String quotationNo);
    boolean existsByQuotationNo(String quotationNo);
    Page<Quotation> findByBranchId(Long branchId, Pageable pageable);
    Page<Quotation> findByBranchIdAndStatus(Long branchId, String status, Pageable pageable);
    Page<Quotation> findByBranchIdAndCustomerId(Long branchId, Long customerId, Pageable pageable);
    Page<Quotation> findByBranchIdAndCustomerIdAndStatus(Long branchId, Long customerId, String status, Pageable pageable);
}
