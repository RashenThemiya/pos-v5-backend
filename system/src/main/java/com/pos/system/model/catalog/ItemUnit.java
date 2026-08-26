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
        @UniqueConstraint(columnNames = {"item_id", "unit_name"}),
        @UniqueConstraint(columnNames = {"item_id", "master_unit_id"}),
        @UniqueConstraint(columnNames = {"branch_id", "barcode"})
})
public class ItemUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long unitId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long itemId;

    @Column(name = "master_unit_id")
    private Long masterUnitId;

    @Column(nullable = false, length = 30)
    private String unitName;

    // Example:
    // KG = 1
    // 100G = 0.1
    // 50KG_BAG = 50
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal multiplierToBase;

    // manufacturer barcode or internally generated barcode
    @Column(length = 100)
    private String barcode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultSellingPrice;

    @Column(nullable = false)
    private Boolean isBaseUnit = false;

    @Column(nullable = false)
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
