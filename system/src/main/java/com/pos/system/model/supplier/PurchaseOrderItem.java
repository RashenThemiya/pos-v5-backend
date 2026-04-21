package com.pos.system.model.supplier;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "purchase_order_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"po_id", "item_id"})
})
public class PurchaseOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long poItemId;

    @Column(nullable = false)
    private Long poId;

    @Column(nullable = false)
    private Long itemId;
@Column(nullable = false)
private Long unitId;
    @Column(nullable = false)
    private BigDecimal orderedQty;

    private BigDecimal receivedQty = BigDecimal.ZERO;
    private BigDecimal unitCostEst;
}
