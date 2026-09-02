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
@Table(name = "item_variants", indexes = {
        @Index(name = "idx_item_variants_item", columnList = "item_id"),
        @Index(name = "idx_item_variants_branch", columnList = "branch_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "sku"})
})
public class ItemVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long variantId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long branchId;

    @Column(length = 100)
    private String sku;

    @Column(precision = 10, scale = 2)
    private BigDecimal defaultSellingPrice;

    @Column(nullable = false)
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
