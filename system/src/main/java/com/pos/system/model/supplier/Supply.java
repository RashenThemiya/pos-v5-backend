package com.pos.system.model.supplier;

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
@Table(name = "supplies")
public class Supply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long supplyId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long supplierId;

    private Long poId;

    @Column(unique = true, length = 50)
    private String grnNo;

    @Column(length = 50)
    private String invoiceNo;

    // =========================
    // AMOUNTS
    // =========================

    @Column(precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal rounding = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    // =========================
    // PAYMENT TRACKING (🔥 IMPORTANT)
    // =========================

    @Column(precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal payableAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 30)
    private String paymentStatus = "UNPAID";
    // UNPAID | PARTIAL | PAID

    private String paymentMethod;

    // =========================
    // STATUS
    // =========================

    private String status; // COMPLETED / PENDING

    private LocalDateTime supplyDate;

    @Column(nullable = false)
    private Long receivedBy;

    private String notes;
}