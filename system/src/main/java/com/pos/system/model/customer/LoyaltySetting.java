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
@Table(name = "loyalty_settings")
public class LoyaltySetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long settingId;

    @Column(nullable = false, unique = true)
    private Long branchId;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal pointsPerCurrency;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal currencyPerPoint;

    private BigDecimal minRedeemPoints = BigDecimal.ZERO;
    private BigDecimal maxRedeemPercent;
    private Boolean isActive = true;
    private LocalDateTime createdAt;
}
