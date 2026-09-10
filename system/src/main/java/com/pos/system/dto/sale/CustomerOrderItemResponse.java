package com.pos.system.dto.sale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOrderItemResponse {
    private Long itemId;
    private Long variantId;
    private String variantSku;
    private String variantLabel;
    private String itemName;
    private BigDecimal quantity;
    private String unitName;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
