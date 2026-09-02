package com.pos.system.dto.item;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ItemVariantRequest {
    private Long variantId;
    private String sku;
    private BigDecimal defaultSellingPrice;
    private Boolean isActive;
    private List<ItemVariantAttributeRequest> attributes;
}
