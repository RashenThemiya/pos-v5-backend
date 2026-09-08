package com.pos.system.repository;

import com.pos.system.model.supplier.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {
    List<SupplierPayment> findBySupplierId(Long supplierId);
    List<SupplierPayment> findBySupplyId(Long supplyId);
    List<SupplierPayment> findByPoId(Long poId);
    @Query("select payment from SupplierPayment payment where payment.poId in :poIds")
    List<SupplierPayment> findByPoIdIn(@Param("poIds") Collection<Long> poIds);
    List<SupplierPayment> findByBranchIdOrderByPaymentDateDesc(Long branchId);

List<SupplierPayment> findByBranchIdAndSupplierIdOrderByPaymentDateDesc(Long branchId, Long supplierId);

List<SupplierPayment> findBySupplyIdOrderByPaymentDateDesc(Long supplyId);
}
