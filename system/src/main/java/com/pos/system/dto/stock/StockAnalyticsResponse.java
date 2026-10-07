package com.pos.system.dto.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalyticsResponse {

    private PageContext context;
    private AnalyticsFilters filters;
    private EmptyState emptyState;
    private StockSummary summary;
    private long unreadNotificationCount;
    private List<StockNotification> notifications;
    private List<MovementBucket> movement;
    private InventoryHealth health;
    private InventoryValue inventoryValue;
    private List<TopSellingProduct> topSellingProducts;
    private List<SlowMovingProduct> slowMovingProducts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageContext {
        private Long branchId;
        private String branchName;
        private String title;
        private String subtitle;
        private String backRoute;
        private List<String> tabs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyticsFilters {
        private String dateRange;
        private LocalDateTime from;
        private LocalDateTime to;
        private String timezone;
        private int productLimit;
        private int slowLimit;
        private int slowWarningDays;
        private int slowCriticalDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmptyState {
        private boolean empty;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockSummary {
        private long totalProducts;
        private long lowStock;
        private long outOfStock;
        private BigDecimal totalAvailableQty;
        private String quantityUnit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockNotification {
        private String id;
        private String type;
        private String severity;
        private String title;
        private String message;
        private boolean read;
        private LocalDateTime createdAt;
        private Long productId;
        private String targetRoute;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationReadResponse {
        private long markedRead;
        private long unreadNotificationCount;
        private List<StockNotification> notifications;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MovementBucket {
        private String bucketKey;
        private String label;
        private LocalDateTime bucketFrom;
        private LocalDateTime bucketTo;
        private BigDecimal receivedQty;
        private BigDecimal soldQty;
        private BigDecimal adjustmentQty;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryHealth {
        private long totalProducts;
        private long healthyCount;
        private long lowStockCount;
        private long outOfStockCount;
        private List<HealthSegment> segments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthSegment {
        private String status;
        private long count;
        private BigDecimal percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryValue {
        private BigDecimal estimatedValue;
        private BigDecimal purchasedValue;
        private BigDecimal soldValue;
        private BigDecimal adjustmentValue;
        private BigDecimal purchasedQty;
        private BigDecimal soldQty;
        private BigDecimal adjustmentQty;
        private String valuationMethod;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopSellingProduct {
        private int rank;
        private Long productId;
        private String productName;
        private String sku;
        private Long categoryId;
        private String categoryName;
        private BigDecimal quantitySold;
        private BigDecimal salesValue;
        private BigDecimal barPercent;
        private String quantityUnit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlowMovingProduct {
        private int rank;
        private Long productId;
        private String productName;
        private String sku;
        private Long categoryId;
        private String categoryName;
        private LocalDateTime lastSoldAt;
        private Long daysSinceLastSale;
        private String status;
        private BigDecimal currentStock;
        private LocalDateTime asOf;
    }
}
