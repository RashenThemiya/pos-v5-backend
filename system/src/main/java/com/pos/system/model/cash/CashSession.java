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
@Table(name = "cash_sessions")
public class CashSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sessionId;

    @Column(nullable = false)
    private Long counterId;

    @Column(nullable = false)
    private Long openedBy;

    private LocalDateTime openedAt;
    private BigDecimal openingCash = BigDecimal.ZERO;
    private Long closedBy;
    private LocalDateTime closedAt;
    private BigDecimal closingCash;
    private BigDecimal expectedCash;
    private BigDecimal cashDifference;
    private String sessionType;
    private String purpose;
    private String status = "OPEN";
}
