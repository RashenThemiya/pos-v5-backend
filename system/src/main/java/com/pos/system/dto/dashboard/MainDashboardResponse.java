package com.pos.system.dto.dashboard;

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
public class MainDashboardResponse {

    private LocalDateTime periodFrom;
    private LocalDateTime periodTo;
    private List<Long> branchIds;

    private SummaryMetrics summary;
    private InventoryCards inventory;
    private SalesPerformance salesPerformance;
    private List<BranchPerformanceEntry> branchPerformance;
    private BranchStatus branchStatus;
    private InventoryHealth inventoryHealth;
    private ProcurementMetrics procurement;
    private ReturnsMetrics returns;
    private OperationsMetrics operations;
    private PromotionMetrics promotions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryMetrics {
        private BigDecimal totalSales;
        private BigDecimal totalSalesChangePercent;
        private BigDecimal netSales;
        private BigDecimal netSalesChangePercent;
        private long orders;
        private BigDecimal ordersChangePercent;
        private long returns;
        private BigDecimal returnsChangePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryCards {
        private BigDecimal inventoryValue;
        private long lowStockItems;
        private long outOfStockItems;
        private long openPurchaseOrders;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesPerformance {
        private String selectedMetric;
        private List<SalesBucket> buckets;
        private SalesBucket peakRevenueBucket;
        private SalesBucket peakOrdersBucket;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesBucket {
        private String bucketKey;
        private String label;
        private LocalDateTime bucketFrom;
        private LocalDateTime bucketTo;
        private BigDecimal revenue;
        private long orders;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchPerformanceEntry {
        private Long branchId;
        private String branchName;
        private BigDecimal totalSales;
        private BigDecimal netSales;
        private long orders;
        private long returns;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchStatus {
        private long totalBranches;
        private long activeBranches;
        private long inactiveBranches;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryHealth {
        private long totalItems;
        private long healthyItems;
        private long lowStockItems;
        private long outOfStockItems;
        private BigDecimal healthyPercent;
        private BigDecimal lowStockPercent;
        private BigDecimal outOfStockPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcurementMetrics {
        private long openPurchaseOrders;
        private long overduePurchaseOrders;
        private BigDecimal outstandingValue;
        private BigDecimal orderedQuantity;
        private BigDecimal receivedQuantity;
        private BigDecimal receivingRatePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnsMetrics {
        private long returns;
        private BigDecimal refundValue;
        private BigDecimal returnRatePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationsMetrics {
        private long activeCounters;
        private long openSessions;
        private long transactions;
        private BigDecimal cashVariance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionMetrics {
        private long activePromotions;
        private long inactivePromotions;
        private List<PromotionBranchPerformance> promotionSalesByBranch;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionBranchPerformance {
        private Long branchId;
        private String branchName;
        private BigDecimal promotionSales;
        private BigDecimal discountAmount;
        private long redemptionCount;
    }
}
