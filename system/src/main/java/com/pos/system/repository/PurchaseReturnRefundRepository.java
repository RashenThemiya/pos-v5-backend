package com.pos.system.repository;

import com.pos.system.model.supplier.PurchaseReturnRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseReturnRefundRepository extends JpaRepository<PurchaseReturnRefund, Long> {
    List<PurchaseReturnRefund> findByPurchaseReturnIdOrderByRefundedAtDesc(Long purchaseReturnId);
}
