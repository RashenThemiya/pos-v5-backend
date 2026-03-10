package com.pos.system.model.catalog;

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
@Table(name = "tax_rates", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "name"})
})
public class TaxRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taxId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal ratePercent;

    private Boolean isActive = true;
    private LocalDateTime createdAt;
}
