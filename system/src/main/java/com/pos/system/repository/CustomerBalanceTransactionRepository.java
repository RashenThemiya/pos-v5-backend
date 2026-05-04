package com.pos.system.repository;

import com.pos.system.model.customer.CustomerBalanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerBalanceTransactionRepository extends JpaRepository<CustomerBalanceTransaction, Long> {
    List<CustomerBalanceTransaction> findByCustomerId(Long customerId);
    List<CustomerBalanceTransaction> findByCustomerIdAndType(Long customerId, String type);
    List<CustomerBalanceTransaction> findByBranchId(Long branchId);
    List<CustomerBalanceTransaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
