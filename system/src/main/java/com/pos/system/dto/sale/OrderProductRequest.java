package com.pos.system.dto.sale;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderProductRequest {
    private Long itemId;
    private Long unitId;
    private String batchBarcode;    // optional — specific batch to deduct from; FIFO if omitted
    private BigDecimal quantity;
    private BigDecimal unitPrice;

    // Item-level promotion resolved by the item-get route.
    // Types: ITEM_PERCENTAGE, ITEM_FIXED, BUY_X_GET_Y, BATCH
    // null if no promotion on this item.
    private Long promotionId;

    // Discount amount for this line calculated from the promotion above.
    // 0 if no promotion.
    private BigDecimal discount;
}
