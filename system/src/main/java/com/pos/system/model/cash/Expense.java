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
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long expenseId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false)
    private BigDecimal amount;

    private String paymentMethod;
    private LocalDateTime expenseDate;

    private Long cashSessionId;

    @Column(nullable = false)
    private Long createdBy;
    private String note;
}
