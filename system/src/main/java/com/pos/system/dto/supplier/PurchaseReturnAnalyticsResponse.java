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
public class PurchaseReturnAnalyticsResponse {

    private PageContext context;
    private AnalyticsFilters filters;
    private EmptyState emptyState;
    private ReturnSummary summary;
    private List<TrendBucket> trend;
    private List<ReturnStatusEntry> returnStatus;
    private RefundStatus refundStatus;
    private List<SupplierReturnEntry> supplierAnalysis;
    private List<AgingBucket> pendingAging;
    private List<ReturnedItemEntry> mostReturnedItems;

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
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyticsFilters {
        private String dateRange;
        private LocalDateTime from;
        private LocalDateTime to;
        private int supplierLimit;
        private int itemLimit;
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
    public static class ReturnSummary {
        private long totalReturns;
        private BigDecimal returnValue;
        private BigDecimal pendingRefund;
        private BigDecimal refundedAmount;
        private BigDecimal returnedQty;
        private String returnedQtyUnit;
        private BigDecimal refundCompRate;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendBucket {
        private String bucketKey;
        private String label;
        private LocalDateTime bucketFrom;
        private LocalDateTime bucketTo;
        private BigDecimal returnValue;
        private long returnCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnStatusEntry {
        private String status;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundStatus {
        private BigDecimal pending;
        private BigDecimal refunded;
        private BigDecimal remaining;
        private String currency;
        private List<RefundStatusEntry> rows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundStatusEntry {
        private String status;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierReturnEntry {
        private Long supplierId;
        private String supplierName;
        private long returnCount;
        private BigDecimal returnedQty;
        private BigDecimal returnValue;
        private BigDecimal barPercent;
        private String metric;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgingBucket {
        private String bucketKey;
        private String label;
        private int minDays;
        private Integer maxDays;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnedItemEntry {
        private int rank;
        private Long productId;
        private String productName;
        private String sku;
        private BigDecimal returnedQty;
        private String unitName;
        private BigDecimal returnValue;
        private String reason;
    }
}
