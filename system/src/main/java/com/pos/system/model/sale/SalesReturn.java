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
    private BigDecimal refundAmount;
    private Long refundPaymentId;
    private String reason;

    @Column(nullable = false)
    private Long processedBy;
    private String status;
}
