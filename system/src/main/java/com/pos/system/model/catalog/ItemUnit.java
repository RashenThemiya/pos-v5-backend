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
@Table(name = "item_units", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"item_id", "unit_name"})
})
public class ItemUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long unitId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 30)
    private String unitName;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal multiplierToBase;

    private String barcode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultSellingPrice;

    private Boolean isBaseUnit = false;
    private Boolean isActive = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
