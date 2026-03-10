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
@Table(name = "purchase_returns")
public class PurchaseReturn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long purchaseReturnId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long supplierId;

    private Long supplyId;
    private LocalDateTime returnDate;
    private String refundMethod;
    private BigDecimal refundAmount;
    private String reason;

    @Column(nullable = false)
    private Long processedBy;

    private String status;
}
