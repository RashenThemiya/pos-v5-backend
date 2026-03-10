package com.pos.system.model.sale;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_return_items")
public class SalesReturnItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long returnItemId;

    @Column(nullable = false)
    private Long returnId;

    @Column(nullable = false)
    private Long itemId;

    private String internalBatchBarcode;
    @Column(nullable = false)
    private BigDecimal quantity;
    private String condition;
    @Column(nullable = false)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private BigDecimal lineRefund;
}
