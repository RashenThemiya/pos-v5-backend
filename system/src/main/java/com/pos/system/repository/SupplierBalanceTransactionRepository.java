package com.pos.system.repository;

import com.pos.system.model.supplier.SupplierBalanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierBalanceTransactionRepository extends JpaRepository<SupplierBalanceTransaction, Long> {
    List<SupplierBalanceTransaction> findBySupplierId(Long supplierId);
}