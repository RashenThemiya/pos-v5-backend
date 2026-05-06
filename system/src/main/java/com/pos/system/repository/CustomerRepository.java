package com.pos.system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.system.model.customer.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    // Find by unique fields
    Optional<Customer> findByPhone(String phone);
    Optional<Customer> findByNic(String nic);
    boolean existsByNic(String nic);
    Optional<Customer> findByLoyaltyCardNo(String loyaltyCardNo);

    // Find by branch
    List<Customer> findByBranchId(Long branchId);

    // Check existence
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndBranchId(String phone, Long branchId);

    Page<Customer> findByBranchId(Long branchId, Pageable pageable);
}
