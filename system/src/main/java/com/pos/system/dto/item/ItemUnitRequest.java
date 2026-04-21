package com.pos.system.dto.item;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItemUnitRequest {
    private String unitName;
    private BigDecimal multiplierToBase;
    private String barcode;
    private BigDecimal defaultSellingPrice;
    private Boolean isBaseUnit;
    private Boolean isActive;
}