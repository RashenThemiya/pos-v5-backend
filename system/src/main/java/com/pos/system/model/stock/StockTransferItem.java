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
@Table(name = "stock_transfer_items")
public class StockTransferItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transferItemId;

    @Column(nullable = false)
    private Long transferId;

    @Column(nullable = false)
    private Long itemId;

    private Long variantId;

    private String internalBatchBarcode;
    @Column(nullable = false)
    private BigDecimal quantity;
}
