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

    @Column(unique = true, length = 60)
    private String returnNo;

    private Long customerId;
    private Long cashSessionId;
    private LocalDateTime returnDate;
    private String refundMethod;
    private String settlementMethod;
    private BigDecimal refundAmount;
    private Long refundPaymentId;
    private String creditNoteNo;
    private String voucherNo;
    private Long exchangeOrderId;
    private String reason;

    @Column(nullable = false)
    private Long processedBy;
    private Long approvedBy;
    private String status;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private Long cancelledBy;
    private String cancelReason;
}
