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
@Table(name = "withdrawals")
public class Withdrawal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long withdrawalId;

    private Long cashSessionId;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(nullable = false)
    private BigDecimal amount;

    private LocalDateTime withdrawalDate;

    @Column(nullable = false)
    private Long userId;

    private String notes;
}
