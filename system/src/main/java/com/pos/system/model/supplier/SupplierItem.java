package com.pos.system.model.supplier;

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
@Table(name = "supplier_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "supplier_id", "item_id", "unit_id"})
})
public class SupplierItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long supplierItemId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long supplierId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long unitId;

    @Column(precision = 14, scale = 2)
    private BigDecimal lastPurchaseCost;

    @Column(precision = 14, scale = 2)
    private BigDecimal defaultCostPrice;

    @Column(nullable = false)
    private Boolean isPreferred = false;

    @Column(nullable = false)
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}