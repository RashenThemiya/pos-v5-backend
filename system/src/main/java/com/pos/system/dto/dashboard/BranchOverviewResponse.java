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
public class BranchOverviewResponse {

    private BranchContext context;
    private DashboardFilters filters;
    private Summary summary;
    private TodaySnapshot snapshot;
    private SalesReturnPerformance salesReturnTrend;
    private PaymentMethods payments;
    private TopProducts products;
    private PromotionPerformance promotions;
    private InventoryStatus inventory;
    private AttentionPanel attention;
    private RecentTransactions transactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchContext {
        private Long branchId;
        private String branchName;
        private String branchCode;
        private String timezone;
        private String address;
        private String phone;
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardFilters {
        private String dateRange;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private BigDecimal monthlyRevenue;
        private BigDecimal monthlyRevenueChange;
        private BigDecimal todaysSales;
        private BigDecimal todaysSalesChange;
        private long totalOrders;
        private BigDecimal totalOrdersChange;
        private long activeStaff;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodaySnapshot {
        private BigDecimal sales;
        private long orders;
        private long customers;
        private BigDecimal refunds;
        private BigDecimal discounts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesReturnPerformance {
        private List<SalesReturnBucket> buckets;
        private long completedOrders;
        private BigDecimal averageOrder;
        private BigDecimal netRevenue;
        private BigDecimal totalRefunds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesReturnBucket {
        private String bucketKey;
        private String label;
        private LocalDateTime bucketFrom;
        private LocalDateTime bucketTo;
        private BigDecimal sales;
        private BigDecimal returns;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethods {
        private BigDecimal totalSales;
        private List<PaymentMethodEntry> methods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodEntry {
        private String method;
        private BigDecimal amount;
        private BigDecimal percentage;
        private long transactionCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProducts {
        private List<TopProductEntry> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProductEntry {
        private int rank;
        private Long productId;
        private String productName;
        private String sku;
        private BigDecimal quantity;
        private BigDecimal salesValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionPerformance {
        private long activeCount;
        private long usageCount;
        private BigDecimal totalDiscountValue;
        private BigDecimal averageDiscount;
        private List<PromotionPerformer> topPerformers;
        private List<PromotionAlert> alerts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionPerformer {
        private Long promotionId;
        private String promotionName;
        private String promoCode;
        private BigDecimal promotionSales;
        private BigDecimal discountValue;
        private long usageCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionAlert {
        private String id;
        private String type;
        private String severity;
        private String title;
        private String description;
        private Long promotionId;
        private String targetRoute;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryStatus {
        private long totalProducts;
        private long inStock;
        private long lowStock;
        private long outOfStock;
        private BigDecimal inStockPercent;
        private BigDecimal lowStockPercent;
        private BigDecimal outOfStockPercent;
        private List<LowStockItem> lowStockItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LowStockItem {
        private Long productId;
        private String productName;
        private String sku;
        private BigDecimal currentQuantity;
        private BigDecimal threshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttentionPanel {
        private List<AttentionAlert> alerts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttentionAlert {
        private String id;
        private String type;
        private String severity;
        private String title;
        private String description;
        private String targetRoute;
        private Long targetId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentTransactions {
        private List<RecentTransaction> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentTransaction {
        private String invoice;
        private String customer;
        private BigDecimal amount;
        private String method;
        private LocalDateTime time;
        private String status;
        private Long orderId;
    }
}
