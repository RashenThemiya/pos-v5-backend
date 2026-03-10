package com.pos.system.model.cash;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cash_session_denominations")
public class CashSessionDenomination {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private BigDecimal denomination;

    @Column(nullable = false)
    private Integer qty;

    @Column(nullable = false)
    private BigDecimal total;
}
