package com.pos.system.dto.sale;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SaleProductSearchResponse {
    private String matchType;
    private ScanDto scan;
    private ItemDto item;
    private UnitDto matchedUnit;
    private BatchDto matchedBatch;
    private StockDto stock;
    private List<UnitDto> units;
    private List<BatchDto> batches;
    private List<PromotionDto> promotions;

    @Data
    @Builder
    public static class ScanDto {
        private String barcode;
        private String scaleItemCode;
        private BigDecimal quantity;
        private String quantityUnit;
        private BigDecimal encodedPrice;
    }

    @Data
    @Builder
    public static class ItemDto {
        private Long itemId;
        private Long branchId;
        private String sku;
        private String name;
        private String image;
        private Long categoryId;
        private Long brandId;
        private Boolean isWeighed;
        private String scaleBarcodePrefix;
        private Boolean isActive;
        private BigDecimal minStock;
        private BigDecimal maxStock;
    }

    @Data
    @Builder
    public static class UnitDto {
        private Long unitId;
        private String unitName;
        private BigDecimal multiplierToBase;
        private String barcode;
        private BigDecimal defaultSellingPrice;
        private Boolean isBaseUnit;
        private Boolean isActive;
    }

    @Data
    @Builder
    public static class StockDto {
        private Long stockId;
        private Long unitId;
        private LocalDateTime lastUpdated;
    }

    @Data
    @Builder
    public static class BatchDto {
        private Long stockBatchId;
        private Long supplyProductId;
        private Long unitId;
        private String unitName;
        private String unitBarcode;
        private BigDecimal receivedQty;
        private BigDecimal receivedBaseQty;
        private BigDecimal qtyRemaining;
        private BigDecimal availableQty;
        private BigDecimal damagedQty;
        private BigDecimal expiredQty;
        private String internalBatchBarcode;
        private String batchNo;
        private String supplierBatchBarcode;
        private LocalDate expiryDate;
        private BigDecimal costPrice;
        private BigDecimal sellingPrice;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class PromotionDto {
        private Long promotionId;
        private String name;
        private String promoCode;
        private String type;
        private BigDecimal value;
        private Integer priority;
        private Boolean isStackable;
        private String appliesBy;
        private Long promotionItemId;
        private Long promotionBatchId;
        private Long unitId;
        private String batchBarcode;
        private BigDecimal maxQty;
        private BigDecimal usedQty;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
    }
}
