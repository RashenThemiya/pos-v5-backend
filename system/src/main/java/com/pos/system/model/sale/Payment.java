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
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @Column(nullable = false)
    private Long branchId;

    private Long orderId;
    private Long customerId;
    private Long cashSessionId;

    @Column(nullable = false)
    private BigDecimal amount;

    private BigDecimal tenderedAmount;
    private BigDecimal changeAmount = BigDecimal.ZERO;
    private String paymentMethod;
    private LocalDateTime paymentDate;
    private Long receivedBy;
    private String referenceNo;
    private String note;
}
