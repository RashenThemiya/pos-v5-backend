package com.pos.system.repository;

import com.pos.system.model.customer.Customer;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
  
    List<Customer> findByBranchId(Long branchId);
  
    Optional<Customer> findByLoyaltyCardNo(String loyaltyCardNo);
  
    Optional<Customer> findByAuthId(Long authId);
  
    Optional<Customer> findByPhone(String phone);
  
    Optional<Customer> findByNic(String nic);
  
    boolean existsByNic(String nic);
  
    boolean existsByPhone(String phone);
  
    boolean existsByPhoneAndBranchId(String phone, Long branchId);
  
    Page<Customer> findByBranchId(Long branchId, Pageable pageable);

    Page<Customer> findAll(Specification<Customer> specification, Pageable pageable);
}
