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
@Table(name = "purchase_return_refunds")
public class PurchaseReturnRefund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long refundId;

    @Column(nullable = false)
    private Long purchaseReturnId;
    @Column(nullable = false)
    private Long branchId;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private String refundMethod;
    private String referenceNo;
    private String bankReference;
    private Long counterId;
    private Long cashSessionId;
    @Column(nullable = false)
    private Long processedBy;
    @Column(nullable = false)
    private LocalDateTime refundedAt;
}
