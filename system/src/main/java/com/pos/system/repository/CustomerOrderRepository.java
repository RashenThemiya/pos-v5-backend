package com.pos.system.repository;

import com.pos.system.model.sale.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    // ── Existing queries ─────────────────────────────────────────────────

    Optional<CustomerOrder> findByInvoiceNo(String invoiceNo);

    boolean existsByInvoiceNo(String invoiceNo);

    List<CustomerOrder> findByBranchIdOrderByOrderDateDesc(Long branchId);

    List<CustomerOrder> findByBranchIdAndCashSessionIdOrderByOrderDateDesc(Long branchId, Long cashSessionId);

    List<CustomerOrder> findByBranchIdAndCustomerIdOrderByOrderDateDesc(Long branchId, Long customerId);

    List<CustomerOrder> findByBranchIdAndOrderDateBetweenOrderByOrderDateDesc(
            Long branchId, LocalDateTime from, LocalDateTime to);

    List<CustomerOrder> findByBranchIdAndStatusOrderByOrderDateDesc(Long branchId, String status);

    // ── Dashboard aggregate queries ───────────────────────────────────────

    /** Count orders by branch and status within a date range. */
    @Query("SELECT COUNT(o) FROM CustomerOrder o " +
           "WHERE o.branchId = :branchId " +
           "AND o.orderDate BETWEEN :from AND :to " +
           "AND o.status = :status")
    long countByBranchAndStatusAndPeriod(
            @Param("branchId") Long branchId,
            @Param("status")   String status,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to);

    /** Count all orders for a branch in a date range regardless of status. */
    @Query("SELECT COUNT(o) FROM CustomerOrder o " +
           "WHERE o.branchId = :branchId " +
           "AND o.orderDate BETWEEN :from AND :to")
    long countByBranchAndPeriod(
            @Param("branchId") Long branchId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to);

    /** Sum of totals for completed (COMPLETED) orders in the period. */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM CustomerOrder o " +
           "WHERE o.branchId = :branchId " +
           "AND o.status = 'COMPLETED' " +
           "AND o.orderDate BETWEEN :from AND :to")
    BigDecimal sumGrossRevenue(
            @Param("branchId") Long branchId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to);

    /** Sum of discounts for completed orders in the period. */
    @Query("SELECT COALESCE(SUM(o.discount), 0) FROM CustomerOrder o " +
           "WHERE o.branchId = :branchId " +
           "AND o.status = 'COMPLETED' " +
           "AND o.orderDate BETWEEN :from AND :to")
    BigDecimal sumDiscount(
            @Param("branchId") Long branchId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to);

    /** Sum of tax for completed orders in the period. */
    @Query("SELECT COALESCE(SUM(o.taxAmount), 0) FROM CustomerOrder o " +
           "WHERE o.branchId = :branchId " +
           "AND o.status = 'COMPLETED' " +
           "AND o.orderDate BETWEEN :from AND :to")
    BigDecimal sumTax(
            @Param("branchId") Long branchId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to);

    /**
     * Top-selling items by quantity sold for a branch in a period.
     * Returns Object[] rows: [itemId, itemName, sku, quantitySold, revenue]
     */
    @Query("SELECT op.itemId, i.name, i.sku, " +
           "       SUM(op.quantity) AS qty, " +
           "       SUM(op.lineTotal) AS revenue " +
           "FROM CustomerOrder o " +
           "JOIN OrderProduct op ON op.orderId = o.orderId " +
           "JOIN Item i          ON i.itemId   = op.itemId " +
           "WHERE o.branchId = :branchId " +
           "AND o.status = 'COMPLETED' " +
           "AND o.orderDate BETWEEN :from AND :to " +
           "GROUP BY op.itemId, i.name, i.sku " +
           "ORDER BY qty DESC")
    List<Object[]> findTopItemsByBranchAndPeriod(
            @Param("branchId") Long branchId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            org.springframework.data.domain.Pageable pageable);

    /** Distinct customers who placed at least one order in the period. */
    @Query("SELECT COUNT(DISTINCT o.customerId) FROM CustomerOrder o " +
           "WHERE o.branchId  = :branchId " +
           "AND o.customerId IS NOT NULL " +
           "AND o.orderDate BETWEEN :from AND :to")
    long countActiveCustomers(
            @Param("branchId") Long branchId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to);
}
