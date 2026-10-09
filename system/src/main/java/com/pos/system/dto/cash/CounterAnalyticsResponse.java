package com.pos.system.dto.cash;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterAnalyticsResponse {

    private PageContext context;
    private AnalyticsFilters filters;
    private EmptyState emptyState;
    private CounterSummary summary;
    private long unreadAlertCount;
    private List<VarianceAlert> varianceAlerts;
    private List<SalesPerformanceBucket> salesPerformance;
    private List<CounterPerformanceEntry> counterPerformance;
    private CashFlow cashFlow;
    private List<HourlySalesTrend> salesTrend;
    private List<CounterUsageEntry> counterUsage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageContext {
        private Long branchId;
        private String branchName;
        private String title;
        private String description;
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
        private LocalDate trendDate;
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
    public static class CounterSummary {
        private BigDecimal netSales;
        private BigDecimal netSalesChangePercentage;
        private long transactions;
        private BigDecimal transactionsChangePercentage;
        private BigDecimal averageTransaction;
        private BigDecimal averageTransactionChangePercentage;
        private long cashSessions;
        private BigDecimal cashSessionsChangePercentage;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VarianceAlert {
        private String id;
        private String type;
        private String severity;
        private String title;
        private String message;
        private boolean read;
        private LocalDateTime createdAt;
        private Long sessionId;
        private Long counterId;
        private String counterName;
        private BigDecimal expectedAmount;
        private BigDecimal actualAmount;
        private BigDecimal variance;
        private String currency;
        private String targetRoute;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationReadResponse {
        private long markedRead;
        private long unreadAlertCount;
        private List<VarianceAlert> varianceAlerts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesPerformanceBucket {
        private String bucketKey;
        private String label;
        private LocalDateTime bucketFrom;
        private LocalDateTime bucketTo;
        private BigDecimal revenue;
        private long transactions;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CounterPerformanceEntry {
        private int rank;
        private Long counterId;
        private String counterName;
        private BigDecimal revenue;
        private long transactions;
        private BigDecimal revenueBarPercent;
        private BigDecimal transactionBarPercent;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashFlow {
        private BigDecimal cashIn;
        private BigDecimal cashOut;
        private BigDecimal netCashFlow;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlySalesTrend {
        private String hour;
        private String label;
        private LocalDateTime bucketFrom;
        private LocalDateTime bucketTo;
        private BigDecimal actualSales;
        private long transactions;
        private BigDecimal normalizedScore;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CounterUsageEntry {
        private Long counterId;
        private String counterName;
        private long transactions;
        private BigDecimal usagePercentage;
    }
}
