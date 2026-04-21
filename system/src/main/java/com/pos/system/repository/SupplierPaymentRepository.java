package com.pos.system.repository;

import com.pos.system.model.supplier.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {
    List<SupplierPayment> findBySupplierId(Long supplierId);
    List<SupplierPayment> findBySupplyId(Long supplyId);
}