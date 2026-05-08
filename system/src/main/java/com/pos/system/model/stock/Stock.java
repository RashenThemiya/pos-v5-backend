package com.pos.system.model.stock;

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
@Table(name = "stock", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "item_id"})
})
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long unitId;

    

    // always stored in base unit
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal availableQty = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal damagedQty = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal expiredQty = BigDecimal.ZERO;

    private LocalDateTime lastUpdated;
}