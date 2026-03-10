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
@Table(name = "stock_movements")
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long movementId;

    @Column(nullable = false)
    private Long branchId;

    private String movementType;
    private Long itemId;
    private String internalBatchBarcode;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal unitPrice;
    private String refTable;
    private Long refId;
    private String note;

    @Column(nullable = false)
    private Long createdBy;

    private LocalDateTime createdAt;
}
