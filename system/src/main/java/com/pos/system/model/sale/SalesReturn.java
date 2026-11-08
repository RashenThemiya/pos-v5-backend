package com.pos.system.model.sale;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_returns")
public class SalesReturn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long returnId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long orderId;

    private Long customerId;
    private LocalDateTime returnDate;
    private String refundMethod;
    /** Amount paid back to the customer through the refund method (cash, card or voucher). */
    private BigDecimal refundAmount;
    /** Net paid value of the goods returned. Equals refundAmount + balanceAdjustment. Null on legacy rows. */
    private BigDecimal returnValue;
    /** Part of the return value used to reduce what the customer still owed on the order. */
    private BigDecimal balanceAdjustment;
    /** Amount credited to the customer's account (shop balance) instead of being paid out. */
    private BigDecimal accountCredit;
    /** Part of accountCredit that reverses a CREDIT (on-account) payment made for this order. */
    private BigDecimal creditReversal;
    private Long refundPaymentId;
    private String reason;

    @Column(nullable = false)
    private Long processedBy;
    private String status;
}
