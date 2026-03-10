package com.pos.system.model.customer;

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
@Table(name = "loyalty_transactions")
public class LoyaltyTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long loyaltyTxnId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long customerId;

    private Long orderId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private BigDecimal points;

    private BigDecimal valueAmount;
    private String note;

    @Column(nullable = false)
    private Long createdBy;

    private LocalDateTime createdAt;
}
