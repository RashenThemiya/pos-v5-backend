package com.pos.system.repository;

import com.pos.system.model.customer.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByBranchId(Long branchId);
    Optional<Customer> findByLoyaltyCardNo(String loyaltyCardNo);
    Optional<Customer> findByAuthId(Long authId);
}
