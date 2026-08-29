package com.pos.system.dto.stock;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StockResponseDto {
    private Long stockId;
    private Long branchId;
    private Long itemId;
    private String itemSku;
    private String itemName;
    private String itemImage;
    private Boolean itemIsWeighed;
    private Boolean itemIsActive;
    private BigDecimal minStock;
    private BigDecimal maxStock;
    private Long categoryId;
    private String categoryName;
    private Boolean categoryIsActive;
    private Long parentCategoryId;
    private String parentCategoryName;
    private Boolean parentCategoryIsActive;
    private Long subCategoryId;
    private String subCategoryName;
    private Boolean subCategoryIsActive;
    private Long brandId;
    private String brandName;
    private Boolean brandIsActive;
    private List<UnitStockDto> unitStocks;
    private LocalDateTime lastUpdated;

    @Data
    @Builder
    public static class UnitStockDto {
        private Long unitId;
        private Long masterUnitId;
        private String unitName;
        private String unitBarcode;
        private BigDecimal multiplierToBase;
        private BigDecimal defaultSellingPrice;
        private Boolean isBaseUnit;
        private Boolean isActive;
        private BigDecimal availableQty;
        private BigDecimal damagedQty;
        private BigDecimal expiredQty;
    }
}
