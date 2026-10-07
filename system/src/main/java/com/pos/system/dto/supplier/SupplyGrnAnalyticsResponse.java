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
public class SupplyGrnAnalyticsResponse {

    private PageContext context;
    private AnalyticsFilters filters;
    private EmptyState emptyState;
    private GrnSummary kpis;
    private List<ReceivingTrendBucket> receivingTrend;
    private ReceivingStatus receivingStatus;
    private GrnPaymentStatus paymentStatus;
    private ReceivingCompletion completion;
    private List<SupplierReceivingEntry> supplierReceiving;
    private List<TopReceivedItem> topReceivedItems;

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
        private String timezone;
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
    public static class GrnSummary {
        private long totalGrns;
        private BigDecimal receivedValue;
        private BigDecimal receivedQty;
        private long pendingPartial;
        private BigDecimal paidAmount;
        private BigDecimal balanceDue;
        private BigDecimal damagedQty;
        private String currency;
        private String quantityUnit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceivingTrendBucket {
        private String bucketKey;
        private String label;
        private LocalDateTime bucketFrom;
        private LocalDateTime bucketTo;
        private BigDecimal receivedValue;
        private BigDecimal receivedQty;
        private long grnCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceivingStatus {
        private long totalGrns;
        private long completed;
        private long partial;
        private long pending;
        private BigDecimal completionPercentage;
        private List<ReceivingStatusRow> rows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceivingStatusRow {
        private String status;
        private long count;
        private BigDecimal percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrnPaymentStatus {
        private BigDecimal totalAmount;
        private String currency;
        private List<PaymentStatusSegment> segments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentStatusSegment {
        private String status;
        private BigDecimal amount;
        private BigDecimal percentage;
        private long grnCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceivingCompletion {
        private BigDecimal expectedQty;
        private BigDecimal receivedQty;
        private BigDecimal remainingQty;
        private BigDecimal completionPercentage;
        private String quantityUnit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierReceivingEntry {
        private int rank;
        private Long supplierId;
        private String supplierName;
        private BigDecimal receivedValue;
        private BigDecimal receivedQty;
        private long grnCount;
        private BigDecimal barPercent;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopReceivedItem {
        private int rank;
        private Long productId;
        private String productName;
        private String sku;
        private BigDecimal receivedQty;
        private BigDecimal receivedValue;
        private String unitName;
    }
}
