package com.pos.system.model.stock;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_count_items")
public class StockCountItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockCountItemId;

    @Column(nullable = false)
    private Long stockCountId;

    @Column(nullable = false)
    private Long itemId;

    private Long variantId;

    private BigDecimal systemQty;
    private BigDecimal countedQty;
    private BigDecimal differenceQty;
    private String note;
}
