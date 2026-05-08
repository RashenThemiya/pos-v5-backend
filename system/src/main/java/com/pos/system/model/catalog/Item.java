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
@Table(name = "items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "sku"})
})
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    private String image;
    private Long categoryId;
    private Long brandId;

    @Column(nullable = false)
    private Boolean isWeighed = false;

    // ===== NEW STOCK CONTROL =====
    @Column(precision = 12, scale = 3)
    private BigDecimal minStock;   // reorder level

    @Column(precision = 12, scale = 3)
    private BigDecimal maxStock;   // max allowed stock


    // optional: only for items sold through scale barcode flow
    private String scaleBarcodePrefix;

    @Column(nullable = false)
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}