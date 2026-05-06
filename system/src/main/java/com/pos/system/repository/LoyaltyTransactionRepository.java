package com.pos.system.repository;

import com.pos.system.model.customer.LoyaltyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {
    List<LoyaltyTransaction> findByCustomerId(Long customerId);
    List<LoyaltyTransaction> findByCustomerIdAndType(Long customerId, String type);
    List<LoyaltyTransaction> findByBranchId(Long branchId);
    List<LoyaltyTransaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}