package com.pos.system.dto.sale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderProductResponse {
    private Long orderProductId;
    private Long orderId;
    private Long itemId;
    private Long variantId;
    private String variantSku;
    private String variantLabel;
    private String itemName;
    private Long unitId;
    private Long masterUnitId;
    private String unitName;
    private String batchBarcode;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discount;
    private BigDecimal lineTotal;
    private LocalDateTime createdAt;
}
