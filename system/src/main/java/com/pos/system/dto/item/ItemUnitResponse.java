package com.pos.system.dto.item;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ItemUnitResponse {
    private Long unitId;
    private Long itemId;
    private Long masterUnitId;
    private String unitName;
    private BigDecimal multiplierToBase;
    private String barcode;
    private BigDecimal defaultSellingPrice;
    private Boolean isBaseUnit;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
