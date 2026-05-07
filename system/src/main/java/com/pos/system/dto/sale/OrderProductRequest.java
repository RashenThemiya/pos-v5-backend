package com.pos.system.dto.sale;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderProductRequest {
    private Long itemId;
    private Long unitId;
    private String batchBarcode;     // optional — specific batch to deduct from
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discount;     // per-line discount amount
}
