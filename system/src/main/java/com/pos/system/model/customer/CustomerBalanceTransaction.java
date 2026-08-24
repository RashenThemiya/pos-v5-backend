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
@Table(name = "customer_balance_transactions")
public class CustomerBalanceTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long txnId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private BigDecimal amount;

    private BigDecimal previousBalance;
    private BigDecimal newBalance;

    private String refTable;
    private Long refId;
    private String note;

    @Column(nullable = false)
    private Long createdBy;

    private LocalDateTime createdAt;
}
