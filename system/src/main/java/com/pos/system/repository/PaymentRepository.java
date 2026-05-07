package com.pos.system.repository;

import com.pos.system.model.sale.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderId(Long orderId);
    List<Payment> findByCashSessionId(Long cashSessionId);
    List<Payment> findByBranchId(Long branchId);
}
