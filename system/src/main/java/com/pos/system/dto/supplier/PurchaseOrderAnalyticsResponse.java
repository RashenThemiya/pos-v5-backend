package com.pos.system.dto.supplier;

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
public class PurchaseOrderAnalyticsResponse {

    private PageContext context;
    private AnalyticsFilters filters;
    private EmptyState emptyState;
    private Summary summary;
    private List<SpendBucket> spendTrend;
    private StatusBreakdown status;
    private ReceivingPerformance receivingPerformance;
    private List<ValueDistribution> valueDistribution;
    private PaymentExposure paymentExposure;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageContext {
        private Long branchId;
        private String branchName;
        private String title;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyticsFilters {
        private String dateRange;
        private LocalDateTime from;
        private LocalDateTime to;
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
    public static class Summary {
        private long totalPurchaseOrders;
        private BigDecimal poSpend;
        private long openPurchaseOrders;
        private long overduePurchaseOrders;
        private BigDecimal balance;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpendBucket {
        private String bucketKey;
        private String label;
        private LocalDateTime bucketFrom;
        private LocalDateTime bucketTo;
        private long purchaseOrderCount;
        private BigDecimal poValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusBreakdown {
        private long totalPurchaseOrders;
        private long open;
        private long completed;
        private long cancelled;
        private long partiallyReceived;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceivingPerformance {
        private BigDecimal orderedQuantity;
        private BigDecimal receivedQuantity;
        private BigDecimal remainingQuantity;
        private BigDecimal completionPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValueDistribution {
        private String bracketKey;
        private String label;
        private BigDecimal minValue;
        private BigDecimal maxValue;
        private long purchaseOrderCount;
        private BigDecimal percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentExposure {
        private BigDecimal paid;
        private BigDecimal partiallyPaid;
        private BigDecimal outstanding;
        private String currency;
        private List<PaymentExposureRow> rows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentExposureRow {
        private String key;
        private String label;
        private BigDecimal amount;
        private long purchaseOrderCount;
    }
}
