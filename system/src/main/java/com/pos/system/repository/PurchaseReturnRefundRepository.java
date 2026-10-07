package com.pos.system.repository;

import com.pos.system.model.supplier.PurchaseReturnRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PurchaseReturnRefundRepository extends JpaRepository<PurchaseReturnRefund, Long> {
    List<PurchaseReturnRefund> findByPurchaseReturnIdOrderByRefundedAtDesc(Long purchaseReturnId);

    @Query("select refund from PurchaseReturnRefund refund where refund.purchaseReturnId in :purchaseReturnIds")
    List<PurchaseReturnRefund> findByPurchaseReturnIdIn(@Param("purchaseReturnIds") Collection<Long> purchaseReturnIds);
}
