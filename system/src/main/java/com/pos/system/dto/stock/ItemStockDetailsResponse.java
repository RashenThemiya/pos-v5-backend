package com.pos.system.dto.stock;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ItemStockDetailsResponse {
    private Long itemId;
    private Long branchId;
    private BaseUnitDto baseUnit;
    private boolean supportsReservedStock;
    private List<VariantStockDto> variants;

    @Data @Builder
    public static class BaseUnitDto {
        private Long unitId;
        private String unitName;
        private BigDecimal multiplierToBase;
    }

    @Data @Builder
    public static class VariantStockDto {
        private Long variantId;
        private String variantName;
        private String sku;
        private String imageUrl;
        private Boolean isActive;
        private List<AttributeDto> attributes;
        private StockTotalsDto stock;
        private List<UnitStockDto> unitStocks;
        private List<BatchDto> batches;
    }

    @Data @Builder
    public static class AttributeDto {
        private String name;
        private String value;
    }

    @Data @Builder
    public static class StockTotalsDto {
        private BigDecimal totalBaseQty;
        private BigDecimal availableBaseQty;
        private BigDecimal reservedBaseQty;
        private BigDecimal damagedBaseQty;
        private BigDecimal expiredBaseQty;
    }

    @Data @Builder
    public static class UnitStockDto {
        private Long unitId;
        private String unitName;
        private BigDecimal multiplierToBase;
        private Boolean isBaseUnit;
        private BigDecimal totalBaseQty;
        private BigDecimal availableBaseQty;
    }

    @Data @Builder
    public static class BatchDto {
        private Long stockBatchId;
        private Long variantId;
        private String batchNo;
        private String internalBatchBarcode;
        private String supplierBatchBarcode;
        private LocalDate receivedDate;
        private LocalDate expiryDate;
        private Long receivedUnitId;
        private String receivedUnitName;
        private BigDecimal receivedUnitMultiplier;
        private BigDecimal originalReceivedQty;
        private BigDecimal originalBaseQty;
        private BigDecimal availableBaseQty;
        private BigDecimal reservedBaseQty;
        private BigDecimal damagedBaseQty;
        private BigDecimal expiredBaseQty;
        private String status;
    }
}
