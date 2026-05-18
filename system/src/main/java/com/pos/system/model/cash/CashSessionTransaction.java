package com.pos.system.model.cash;

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
@Table(name = "cash_session_transactions")
public class CashSessionTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    private String type;
    @Column(nullable = false)
    private BigDecimal amount;
    private String paymentMethod;
    private Long paymentId;
    private Long supplierPaymentId;
    private Long purchaseReturnId;
    private Long expenseId;
    private Long withdrawalId;
    private String note;

    @Column(nullable = false)
    private Long createdBy;
    private LocalDateTime createdAt;
}
