package com.pos.system.repository;

import com.pos.system.model.customer.CustomerBalanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerBalanceTransactionRepository extends JpaRepository<CustomerBalanceTransaction, Long> {
    List<CustomerBalanceTransaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<CustomerBalanceTransaction> findByBranchIdAndCustomerIdOrderByCreatedAtDesc(Long branchId, Long customerId);
}
