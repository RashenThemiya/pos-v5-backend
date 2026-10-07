package com.pos.system.dto.promotion;

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
public class PromotionAnalyticsResponse {

    private PageContext context;
    private AnalyticsFilters filters;
    private Summary summary;
    private List<TrendBucket> trend;
    private List<TypePerformance> typePerformance;
    private StatusBreakdown status;
    private List<TopPromotion> topPromotions;
    private List<Notification> notifications;
    private long unreadNotificationCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageContext {
        private Long branchId;
        private String branchName;
        private String title;
        private String subtitle;
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
        private String sortBy;
        private int topPromotionLimit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private long totalPromotions;
        private long activePromotions;
        private long redemptions;
        private BigDecimal discountGiven;
        private BigDecimal revenue;
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
        private long redemptions;
        private BigDecimal discountGiven;
        private BigDecimal sales;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypePerformance {
        private String type;
        private long redemptions;
        private BigDecimal discountGiven;
        private BigDecimal revenue;
        private BigDecimal percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusBreakdown {
        private long totalPromotions;
        private long activePromotions;
        private long inactivePromotions;
        private BigDecimal activePercent;
        private BigDecimal inactivePercent;
        private String primaryStatus;
        private BigDecimal primaryPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopPromotion {
        private int rank;
        private Long promotionId;
        private String promotionName;
        private String type;
        private long redemptions;
        private BigDecimal discountGiven;
        private BigDecimal revenueGenerated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Notification {
        private String id;
        private String type;
        private String title;
        private String message;
        private boolean read;
        private LocalDateTime timestamp;
        private Long promotionId;
        private String actionLabel;
        private String targetRoute;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationReadResponse {
        private long markedRead;
        private long unreadNotificationCount;
        private List<Notification> notifications;
    }
}
