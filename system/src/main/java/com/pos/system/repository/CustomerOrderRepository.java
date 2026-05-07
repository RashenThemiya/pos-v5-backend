package com.pos.system.repository;

import com.pos.system.model.sale.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByInvoiceNo(String invoiceNo);
    boolean existsByInvoiceNo(String invoiceNo);
    List<CustomerOrder> findByBranchIdOrderByOrderDateDesc(Long branchId);
    List<CustomerOrder> findByBranchIdAndCashSessionIdOrderByOrderDateDesc(Long branchId, Long cashSessionId);
    List<CustomerOrder> findByBranchIdAndCustomerIdOrderByOrderDateDesc(Long branchId, Long customerId);
    List<CustomerOrder> findByBranchIdAndOrderDateBetweenOrderByOrderDateDesc(Long branchId, LocalDateTime from, LocalDateTime to);
    List<CustomerOrder> findByBranchIdAndStatusOrderByOrderDateDesc(Long branchId, String status);
}
