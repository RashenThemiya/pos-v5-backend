package com.pos.system.repository;

import com.pos.system.model.supplier.SupplierPaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierPaymentAllocationRepository extends JpaRepository<SupplierPaymentAllocation, Long> {
    List<SupplierPaymentAllocation> findBySupplierPaymentIdOrderByAllocationIdAsc(Long supplierPaymentId);
}
