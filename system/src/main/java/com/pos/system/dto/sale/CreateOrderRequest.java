package com.pos.system.dto.sale;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRequest {
    private Long branchId;
    private Long userId;
    private Long customerId;        // nullable for walk-in
    private Long cashSessionId;
    private Long promotionId;       // nullable
    private List<OrderProductRequest> items;
    private BigDecimal discount;    // bill-level discount
    private BigDecimal rounding;    // rounding adjustment
    private String notes;
}
