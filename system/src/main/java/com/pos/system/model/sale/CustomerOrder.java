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
@Table(name = "customer_orders")
public class CustomerOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, unique = true, length = 50)
    private String invoiceNo;

    private Long userId;
    private Long customerId;
    private Long cashSessionId;

    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal rounding = BigDecimal.ZERO;
    private BigDecimal total;
    private String paymentStatus = "UNPAID";
    private String status;
    private LocalDateTime orderDate;
    private String notes;
    private LocalDateTime cancelledAt;
    private Long cancelledBy;
    private String cancelReason;
    private LocalDateTime updatedAt;
}
