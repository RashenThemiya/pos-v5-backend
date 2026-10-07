package com.pos.system.dto.sale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesReturnAnalyticsResponse {

    private PageContext context;
    private AnalyticsFilters filters;
    private EmptyState emptyState;
    private KpiSummary summary;
    private List<PerformanceBucket> performance;
    private CustomerEngagement customerEngagement;
    private PaymentMethods paymentMethods;
    private TopProducts topProducts;
    private ReturnPerformance returnPerformance;
    private ActivityResponse activity;

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
        private LocalTime activityStartTime;
        private LocalTime activityEndTime;
        private int activitySlotMinutes;
        private int productLimit;
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
    public static class KpiSummary {
        private long totalSales;
        private long totalReturns;
        private BigDecimal grossSalesAmount;
        private BigDecimal returnAmount;
        private BigDecimal netRevenue;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceBucket {
        private String bucketKey;
        private String label;
        private LocalDateTime bucketFrom;
        private LocalDateTime bucketTo;
        private long salesCount;
        private long returnCount;
        private BigDecimal salesAmount;
        private BigDecimal returnAmount;
        private BigDecimal netAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerEngagement {
        private long totalTransactions;
        private List<CustomerSegment> segments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerSegment {
        private String type;
        private long count;
        private BigDecimal percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethods {
        private BigDecimal totalAmount;
        private String currency;
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
        private String unitName;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnPerformance {
        private long returnedOrders;
        private BigDecimal refundAmount;
        private BigDecimal averageRefundPerReturn;
        private BigDecimal refundRate;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityResponse {
        private LocalTime startTime;
        private LocalTime endTime;
        private int slotMinutes;
        private List<ActivityBucket> buckets;
        private List<ActivityPeak> peaks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityBucket {
        private String bucketKey;
        private String label;
        private LocalTime startTime;
        private LocalTime endTime;
        private long salesCount;
        private long returnCount;
        private BigDecimal salesAmount;
        private BigDecimal returnAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityPeak {
        private String metric;
        private String bucketKey;
        private String label;
        private BigDecimal amount;
        private long count;
    }
}
