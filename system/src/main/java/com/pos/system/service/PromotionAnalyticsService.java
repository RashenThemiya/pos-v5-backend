package com.pos.system.service;

import com.pos.system.dto.promotion.PromotionAnalyticsResponse;
import com.pos.system.dto.promotion.PromotionAnalyticsResponse.*;
import com.pos.system.model.auth.Branch;
import com.pos.system.model.promotion.Promotion;
import com.pos.system.model.promotion.PromotionRedemption;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.CustomerOrderRepository;
import com.pos.system.repository.PromotionRedemptionRepository;
import com.pos.system.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromotionAnalyticsService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final String DEFAULT_DATE_RANGE = "30D";
    private static final String DEFAULT_SORT = "REVENUE";
    private static final String CURRENCY = "Rs";
    private static final BigDecimal HIGH_REDEMPTION_THRESHOLD = BigDecimal.valueOf(80);
    private static final BigDecimal MILESTONE_REVENUE = BigDecimal.valueOf(100000);
    private static final long MILESTONE_REDEMPTIONS = 100;

    private final BranchRepository branchRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository promotionRedemptionRepository;
    private final CustomerOrderRepository orderRepository;
    private final Set<String> readNotificationIds = ConcurrentHashMap.newKeySet();

    public PromotionAnalyticsResponse getAnalytics(Long branchId,
                                                   LocalDateTime from,
                                                   LocalDateTime to,
                                                   String dateRange,
                                                   String sortBy,
                                                   int topPromotionLimit) {
        Range range = resolveRange(from, to, dateRange);
        String normalizedSort = normalizeSort(sortBy);
        int safeLimit = sanitizeLimit(topPromotionLimit, 10);
        AnalyticsData data = loadData(branchId, range);
        List<Notification> notifications = buildNotifications(data);

        return PromotionAnalyticsResponse.builder()
                .context(buildContext(data.branch))
                .filters(AnalyticsFilters.builder()
                        .dateRange(resolveDateRangeLabel(dateRange, from, to))
                        .from(range.from())
                        .to(range.to())
                        .sortBy(normalizedSort)
                        .topPromotionLimit(safeLimit)
                        .build())
                .summary(buildSummary(data))
                .trend(buildTrend(data))
                .typePerformance(buildTypePerformance(data))
                .status(buildStatus(data))
                .topPromotions(buildTopPromotions(data, normalizedSort, safeLimit))
                .notifications(notifications)
                .unreadNotificationCount(unreadCount(notifications))
                .build();
    }

    public Summary getSummary(Long branchId,
                              LocalDateTime from,
                              LocalDateTime to,
                              String dateRange) {
        return buildSummary(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<TrendBucket> getTrend(Long branchId,
                                      LocalDateTime from,
                                      LocalDateTime to,
                                      String dateRange) {
        return buildTrend(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<TypePerformance> getTypePerformance(Long branchId,
                                                    LocalDateTime from,
                                                    LocalDateTime to,
                                                    String dateRange) {
        return buildTypePerformance(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public StatusBreakdown getStatus(Long branchId) {
        Branch branch = requireBranch(branchId);
        List<Promotion> promotions = promotionRepository.findByBranchId(branch.getBranchId());
        return buildStatus(new AnalyticsData(
                branch,
                new Range(LocalDateTime.now(), LocalDateTime.now()),
                promotions,
                promotionMap(promotions),
                List.of(),
                Map.of()));
    }

    public List<TopPromotion> getTopPromotions(Long branchId,
                                               LocalDateTime from,
                                               LocalDateTime to,
                                               String dateRange,
                                               String sortBy,
                                               int limit) {
        return buildTopPromotions(
                loadData(branchId, resolveRange(from, to, dateRange)),
                normalizeSort(sortBy),
                sanitizeLimit(limit, 10));
    }

    public List<Notification> getNotifications(Long branchId,
                                               LocalDateTime from,
                                               LocalDateTime to,
                                               String dateRange) {
        return buildNotifications(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public NotificationReadResponse markAllNotificationsRead(Long branchId,
                                                             LocalDateTime from,
                                                             LocalDateTime to,
                                                             String dateRange) {
        AnalyticsData data = loadData(branchId, resolveRange(from, to, dateRange));
        List<Notification> current = buildNotifications(data);
        long markedRead = current.stream()
                .filter(notification -> !notification.isRead())
                .peek(notification -> readNotificationIds.add(notification.getId()))
                .count();
        List<Notification> updated = buildNotifications(data);

        return NotificationReadResponse.builder()
                .markedRead(markedRead)
                .unreadNotificationCount(unreadCount(updated))
                .notifications(updated)
                .build();
    }

    private AnalyticsData loadData(Long branchId, Range range) {
        Branch branch = requireBranch(branchId);
        List<Promotion> promotions = promotionRepository.findByBranchId(branchId);
        Map<Long, Promotion> promotionsById = promotionMap(promotions);
        List<PromotionRedemption> allRedemptions = promotions.stream()
                .flatMap(promotion -> promotionRedemptionRepository
                        .findByPromotionId(promotion.getPromotionId())
                        .stream())
                .filter(redemption -> Objects.equals(branchId, redemption.getBranchId()))
                .toList();

        Set<Long> orderIds = allRedemptions.stream()
                .map(PromotionRedemption::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, CustomerOrder> ordersById = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(CustomerOrder::getOrderId, Function.identity(), (left, right) -> left));

        List<PromotionRedemption> scopedRedemptions = allRedemptions.stream()
                .filter(redemption -> promotionsById.containsKey(redemption.getPromotionId()))
                .filter(redemption -> {
                    CustomerOrder order = ordersById.get(redemption.getOrderId());
                    return order != null
                            && Objects.equals(branchId, order.getBranchId())
                            && isValidOrder(order)
                            && isWithin(redemptionTime(redemption, order), range.from(), range.to());
                })
                .toList();

        return new AnalyticsData(branch, range, promotions, promotionsById, scopedRedemptions, ordersById);
    }

    private PageContext buildContext(Branch branch) {
        return PageContext.builder()
                .branchId(branch.getBranchId())
                .branchName(branch.getName())
                .title("Promotion Analytics")
                .subtitle("Track promotion performance, discount usage, redemption activity, and revenue impact.")
                .tabs(List.of("Overview", "Counter Performance", "Utilization"))
                .build();
    }

    private Summary buildSummary(AnalyticsData data) {
        LocalDateTime now = LocalDateTime.now();
        long activePromotions = data.promotions.stream()
                .filter(promotion -> isActivePromotion(promotion, now))
                .count();

        return Summary.builder()
                .totalPromotions(data.promotions.size())
                .activePromotions(activePromotions)
                .redemptions(data.scopedRedemptions.size())
                .discountGiven(sumDiscounts(data.scopedRedemptions))
                .revenue(sumDistinctOrderRevenue(data.scopedRedemptions, data.ordersById))
                .currency(CURRENCY)
                .build();
    }

    private List<TrendBucket> buildTrend(AnalyticsData data) {
        List<TrendAccumulator> buckets = createBuckets(data.range.from(), data.range.to());

        for (PromotionRedemption redemption : data.scopedRedemptions) {
            CustomerOrder order = data.ordersById.get(redemption.getOrderId());
            if (order == null) {
                continue;
            }

            bucketFor(buckets, redemptionTime(redemption, order))
                    .ifPresent(bucket -> bucket.add(redemption, order));
        }

        return buckets.stream()
                .map(TrendAccumulator::toResponse)
                .toList();
    }

    private List<TypePerformance> buildTypePerformance(AnalyticsData data) {
        Map<String, TypeAccumulator> byType = new HashMap<>();

        for (PromotionRedemption redemption : data.scopedRedemptions) {
            Promotion promotion = data.promotionsById.get(redemption.getPromotionId());
            CustomerOrder order = data.ordersById.get(redemption.getOrderId());
            if (promotion == null || order == null) {
                continue;
            }

            byType.computeIfAbsent(normalizeType(promotion.getType()), TypeAccumulator::new)
                    .add(redemption, order);
        }

        BigDecimal totalRedemptions = BigDecimal.valueOf(data.scopedRedemptions.size());
        return byType.values().stream()
                .map(accumulator -> accumulator.toResponse(totalRedemptions))
                .sorted(Comparator.comparing(TypePerformance::getRedemptions).reversed()
                        .thenComparing(TypePerformance::getRevenue, Comparator.reverseOrder()))
                .toList();
    }

    private StatusBreakdown buildStatus(AnalyticsData data) {
        LocalDateTime now = LocalDateTime.now();
        long active = data.promotions.stream()
                .filter(promotion -> isActivePromotion(promotion, now))
                .count();
        long total = data.promotions.size();
        long inactive = total - active;
        BigDecimal totalDecimal = BigDecimal.valueOf(total);
        BigDecimal activePercent = percent(BigDecimal.valueOf(active), totalDecimal);
        BigDecimal inactivePercent = percent(BigDecimal.valueOf(inactive), totalDecimal);
        boolean activeIsPrimary = active >= inactive;

        return StatusBreakdown.builder()
                .totalPromotions(total)
                .activePromotions(active)
                .inactivePromotions(inactive)
                .activePercent(activePercent)
                .inactivePercent(inactivePercent)
                .primaryStatus(total == 0 ? "NONE" : activeIsPrimary ? "ACTIVE" : "INACTIVE")
                .primaryPercent(total == 0 ? BigDecimal.ZERO : activeIsPrimary ? activePercent : inactivePercent)
                .build();
    }

    private List<TopPromotion> buildTopPromotions(AnalyticsData data, String sortBy, int limit) {
        Map<Long, PromotionAccumulator> byPromotion = new HashMap<>();

        for (PromotionRedemption redemption : data.scopedRedemptions) {
            Promotion promotion = data.promotionsById.get(redemption.getPromotionId());
            CustomerOrder order = data.ordersById.get(redemption.getOrderId());
            if (promotion == null || order == null) {
                continue;
            }

            byPromotion.computeIfAbsent(promotion.getPromotionId(), id -> new PromotionAccumulator(promotion))
                    .add(redemption, order);
        }

        Comparator<PromotionAccumulator> comparator = switch (sortBy) {
            case "REDEMPTIONS" -> Comparator.comparingLong(PromotionAccumulator::redemptions);
            case "DISCOUNT" -> Comparator.comparing(PromotionAccumulator::discountGiven);
            default -> Comparator.comparing(PromotionAccumulator::revenueGenerated);
        };

        List<PromotionAccumulator> ranked = byPromotion.values().stream()
                .sorted(comparator.reversed()
                        .thenComparing(accumulator -> accumulator.promotion.getName(), String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .toList();

        List<TopPromotion> topPromotions = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            topPromotions.add(ranked.get(index).toResponse(index + 1));
        }
        return topPromotions;
    }

    private List<Notification> buildNotifications(AnalyticsData data) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, PromotionAccumulator> byPromotion = accumulatorsByPromotion(data);
        List<Notification> notifications = new ArrayList<>();

        for (Promotion promotion : data.promotions) {
            PromotionAccumulator accumulator = byPromotion.getOrDefault(
                    promotion.getPromotionId(),
                    new PromotionAccumulator(promotion));

            if (isActivePromotion(promotion, now)
                    && promotion.getEndAt() != null
                    && !promotion.getEndAt().isBefore(now)
                    && !promotion.getEndAt().isAfter(now.plusDays(7))) {
                notifications.add(notification(
                        "PROMOTION_EXPIRING-" + promotion.getPromotionId(),
                        "PROMOTION_EXPIRING",
                        "Promotion expiring soon",
                        promotion.getName() + " ends on " + promotion.getEndAt() + ".",
                        promotion.getEndAt(),
                        promotion.getPromotionId(),
                        "View Promotion",
                        "/promotions/" + promotion.getPromotionId()));
            }

            if (promotion.getMaxUsesTotal() != null && promotion.getMaxUsesTotal() > 0) {
                BigDecimal usagePercent = percent(
                        BigDecimal.valueOf(promotionRedemptionRepository.countByPromotionId(promotion.getPromotionId())),
                        BigDecimal.valueOf(promotion.getMaxUsesTotal()));
                if (usagePercent.compareTo(HIGH_REDEMPTION_THRESHOLD) >= 0) {
                    notifications.add(notification(
                            "HIGH_REDEMPTION_RATE-" + promotion.getPromotionId(),
                            "HIGH_REDEMPTION_RATE",
                            "High redemption rate",
                            promotion.getName() + " has used " + usagePercent + "% of its redemption limit.",
                            now,
                            promotion.getPromotionId(),
                            "View Analytics",
                            "/promotions/analytics?promotionId=" + promotion.getPromotionId()));
                }
            }

            if (accumulator.redemptions() >= MILESTONE_REDEMPTIONS
                    || accumulator.revenueGenerated().compareTo(MILESTONE_REVENUE) >= 0) {
                notifications.add(notification(
                        "PERFORMANCE_MILESTONE-" + promotion.getPromotionId() + "-" + rangeKey(data.range),
                        "PERFORMANCE_MILESTONE",
                        "Promotion performance milestone",
                        promotion.getName() + " generated " + CURRENCY + " "
                                + accumulator.revenueGenerated() + " in the selected period.",
                        now,
                        promotion.getPromotionId(),
                        "View Analytics",
                        "/promotions/analytics?promotionId=" + promotion.getPromotionId()));
            }

            if (isActivePromotion(promotion, now) && accumulator.redemptions() == 0) {
                notifications.add(notification(
                        "LOW_PERFORMANCE-" + promotion.getPromotionId() + "-" + rangeKey(data.range),
                        "LOW_PERFORMANCE",
                        "Low promotion performance",
                        promotion.getName() + " has no successful redemptions in the selected period.",
                        now,
                        promotion.getPromotionId(),
                        "View Analytics",
                        "/promotions/analytics?promotionId=" + promotion.getPromotionId()));
            }
        }

        return notifications.stream()
                .sorted(Comparator.comparing(Notification::getTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private Map<Long, PromotionAccumulator> accumulatorsByPromotion(AnalyticsData data) {
        Map<Long, PromotionAccumulator> byPromotion = new HashMap<>();
        for (PromotionRedemption redemption : data.scopedRedemptions) {
            Promotion promotion = data.promotionsById.get(redemption.getPromotionId());
            CustomerOrder order = data.ordersById.get(redemption.getOrderId());
            if (promotion == null || order == null) {
                continue;
            }

            byPromotion.computeIfAbsent(promotion.getPromotionId(), id -> new PromotionAccumulator(promotion))
                    .add(redemption, order);
        }
        return byPromotion;
    }

    private Notification notification(String id,
                                      String type,
                                      String title,
                                      String message,
                                      LocalDateTime timestamp,
                                      Long promotionId,
                                      String actionLabel,
                                      String targetRoute) {
        return Notification.builder()
                .id(id)
                .type(type)
                .title(title)
                .message(message)
                .read(readNotificationIds.contains(id))
                .timestamp(timestamp)
                .promotionId(promotionId)
                .actionLabel(actionLabel)
                .targetRoute(targetRoute)
                .build();
    }

    private Branch requireBranch(Long branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found: " + branchId));
    }

    private Range resolveRange(LocalDateTime from, LocalDateTime to, String dateRange) {
        if (from != null || to != null) {
            if (from == null || to == null) {
                throw new IllegalArgumentException("Both from and to are required for a custom date range");
            }
            validateDateRange(from, to);
            return new Range(from, to);
        }

        LocalDateTime resolvedTo = LocalDateTime.now();
        LocalDateTime resolvedFrom = switch (normalizeDateRange(dateRange)) {
            case "7D" -> resolvedTo.minusDays(7);
            case "3M" -> resolvedTo.minusMonths(3);
            case "6M" -> resolvedTo.minusMonths(6);
            default -> resolvedTo.minusDays(30);
        };
        return new Range(resolvedFrom, resolvedTo);
    }

    private String resolveDateRangeLabel(String dateRange, LocalDateTime from, LocalDateTime to) {
        if (from != null || to != null) {
            return "CUSTOM";
        }
        return normalizeDateRange(dateRange);
    }

    private String normalizeDateRange(String dateRange) {
        if (dateRange == null || dateRange.isBlank()) {
            return DEFAULT_DATE_RANGE;
        }

        String normalized = dateRange.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "7D", "30D", "3M", "6M" -> normalized;
            default -> DEFAULT_DATE_RANGE;
        };
    }

    private String normalizeSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return DEFAULT_SORT;
        }

        String normalized = sortBy.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "REDEMPTIONS", "DISCOUNT", "REVENUE" -> normalized;
            default -> DEFAULT_SORT;
        };
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "UNKNOWN";
        }
        return type.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private Map<Long, Promotion> promotionMap(List<Promotion> promotions) {
        return promotions.stream()
                .collect(Collectors.toMap(Promotion::getPromotionId, Function.identity(), (left, right) -> left));
    }

    private boolean isActivePromotion(Promotion promotion, LocalDateTime now) {
        return Boolean.TRUE.equals(promotion.getIsActive())
                && (promotion.getStartAt() == null || !promotion.getStartAt().isAfter(now))
                && (promotion.getEndAt() == null || !promotion.getEndAt().isBefore(now));
    }

    private boolean isValidOrder(CustomerOrder order) {
        return "COMPLETED".equalsIgnoreCase(safe(order.getStatus()));
    }

    private LocalDateTime redemptionTime(PromotionRedemption redemption, CustomerOrder order) {
        if (redemption.getUsedAt() != null) {
            return redemption.getUsedAt();
        }
        return order != null ? order.getOrderDate() : null;
    }

    private BigDecimal sumDiscounts(List<PromotionRedemption> redemptions) {
        return redemptions.stream()
                .map(redemption -> nvl(redemption.getDiscountAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumDistinctOrderRevenue(List<PromotionRedemption> redemptions,
                                               Map<Long, CustomerOrder> ordersById) {
        Set<Long> orderIds = new HashSet<>();
        BigDecimal revenue = BigDecimal.ZERO;
        for (PromotionRedemption redemption : redemptions) {
            CustomerOrder order = ordersById.get(redemption.getOrderId());
            if (order != null && orderIds.add(order.getOrderId())) {
                revenue = revenue.add(nvl(order.getTotal()));
            }
        }
        return revenue;
    }

    private List<TrendAccumulator> createBuckets(LocalDateTime from, LocalDateTime to) {
        long dayCount = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()) + 1;
        if (dayCount <= 31) {
            return createDailyBuckets(from, to);
        }
        if (dayCount <= 92) {
            return createWeeklyBuckets(from, to);
        }
        return createMonthlyBuckets(from, to);
    }

    private List<TrendAccumulator> createDailyBuckets(LocalDateTime from, LocalDateTime to) {
        List<TrendAccumulator> buckets = new ArrayList<>();
        int index = 1;
        for (LocalDate date = from.toLocalDate(); !date.isAfter(to.toLocalDate()); date = date.plusDays(1)) {
            LocalDateTime bucketFrom = date.atStartOfDay().isBefore(from) ? from : date.atStartOfDay();
            LocalDateTime bucketTo = date.plusDays(1).atStartOfDay().minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new TrendAccumulator(date.toString(), DAY_LABEL.format(date), bucketFrom, bucketTo, index++));
        }
        return buckets;
    }

    private List<TrendAccumulator> createWeeklyBuckets(LocalDateTime from, LocalDateTime to) {
        List<TrendAccumulator> buckets = new ArrayList<>();
        LocalDateTime cursor = from;
        int index = 1;
        while (!cursor.isAfter(to)) {
            LocalDateTime bucketTo = cursor.plusDays(7).minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new TrendAccumulator("week-" + index, "Week " + index, cursor, bucketTo, index));
            cursor = bucketTo.plusNanos(1);
            index++;
        }
        return buckets;
    }

    private List<TrendAccumulator> createMonthlyBuckets(LocalDateTime from, LocalDateTime to) {
        List<TrendAccumulator> buckets = new ArrayList<>();
        LocalDateTime cursor = from;
        int index = 1;
        while (!cursor.isAfter(to)) {
            LocalDateTime nextMonth = cursor.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay();
            LocalDateTime bucketTo = nextMonth.minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new TrendAccumulator(
                    cursor.getYear() + "-" + String.format("%02d", cursor.getMonthValue()),
                    MONTH_LABEL.format(cursor),
                    cursor,
                    bucketTo,
                    index));
            cursor = bucketTo.plusNanos(1);
            index++;
        }
        return buckets;
    }

    private Optional<TrendAccumulator> bucketFor(List<TrendAccumulator> buckets, LocalDateTime value) {
        return buckets.stream()
                .filter(bucket -> isWithin(value, bucket.from, bucket.to))
                .findFirst();
    }

    private void validateDateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("to must be after from");
        }
    }

    private boolean isWithin(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        return value != null && !value.isBefore(from) && !value.isAfter(to);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return nvl(numerator).multiply(BigDecimal.valueOf(100))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private int sanitizeLimit(int limit, int fallback) {
        int value = limit > 0 ? limit : fallback;
        return Math.min(value, 50);
    }

    private String rangeKey(Range range) {
        return range.from().toLocalDate() + "-to-" + range.to().toLocalDate();
    }

    private long unreadCount(List<Notification> notifications) {
        return notifications.stream()
                .filter(notification -> !notification.isRead())
                .count();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record Range(LocalDateTime from, LocalDateTime to) {
    }

    private record AnalyticsData(Branch branch,
                                 Range range,
                                 List<Promotion> promotions,
                                 Map<Long, Promotion> promotionsById,
                                 List<PromotionRedemption> scopedRedemptions,
                                 Map<Long, CustomerOrder> ordersById) {
    }

    private static class TrendAccumulator {
        private final String key;
        private final String label;
        private final LocalDateTime from;
        private final LocalDateTime to;
        private final int index;
        private final Set<Long> orderIds = new HashSet<>();
        private long redemptions;
        private BigDecimal discountGiven = BigDecimal.ZERO;
        private BigDecimal sales = BigDecimal.ZERO;

        private TrendAccumulator(String key, String label, LocalDateTime from, LocalDateTime to, int index) {
            this.key = key;
            this.label = label;
            this.from = from;
            this.to = to;
            this.index = index;
        }

        private void add(PromotionRedemption redemption, CustomerOrder order) {
            redemptions++;
            discountGiven = discountGiven.add(redemption.getDiscountAmount() != null
                    ? redemption.getDiscountAmount()
                    : BigDecimal.ZERO);
            if (orderIds.add(order.getOrderId())) {
                sales = sales.add(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
            }
        }

        private TrendBucket toResponse() {
            return TrendBucket.builder()
                    .bucketKey(key != null ? key : "bucket-" + index)
                    .label(label)
                    .bucketFrom(from)
                    .bucketTo(to)
                    .redemptions(redemptions)
                    .discountGiven(discountGiven)
                    .sales(sales)
                    .build();
        }
    }

    private static class TypeAccumulator {
        private final String type;
        private final Set<Long> orderIds = new HashSet<>();
        private long redemptions;
        private BigDecimal discountGiven = BigDecimal.ZERO;
        private BigDecimal revenue = BigDecimal.ZERO;

        private TypeAccumulator(String type) {
            this.type = type;
        }

        private void add(PromotionRedemption redemption, CustomerOrder order) {
            redemptions++;
            discountGiven = discountGiven.add(redemption.getDiscountAmount() != null
                    ? redemption.getDiscountAmount()
                    : BigDecimal.ZERO);
            if (orderIds.add(order.getOrderId())) {
                revenue = revenue.add(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
            }
        }

        private TypePerformance toResponse(BigDecimal totalRedemptions) {
            BigDecimal percentage = totalRedemptions.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(redemptions).multiply(BigDecimal.valueOf(100))
                    .divide(totalRedemptions, 2, RoundingMode.HALF_UP);

            return TypePerformance.builder()
                    .type(type)
                    .redemptions(redemptions)
                    .discountGiven(discountGiven)
                    .revenue(revenue)
                    .percentage(percentage)
                    .build();
        }
    }

    private static class PromotionAccumulator {
        private final Promotion promotion;
        private final Set<Long> orderIds = new HashSet<>();
        private long redemptions;
        private BigDecimal discountGiven = BigDecimal.ZERO;
        private BigDecimal revenueGenerated = BigDecimal.ZERO;

        private PromotionAccumulator(Promotion promotion) {
            this.promotion = promotion;
        }

        private void add(PromotionRedemption redemption, CustomerOrder order) {
            redemptions++;
            discountGiven = discountGiven.add(redemption.getDiscountAmount() != null
                    ? redemption.getDiscountAmount()
                    : BigDecimal.ZERO);
            if (orderIds.add(order.getOrderId())) {
                revenueGenerated = revenueGenerated.add(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
            }
        }

        private long redemptions() {
            return redemptions;
        }

        private BigDecimal discountGiven() {
            return discountGiven;
        }

        private BigDecimal revenueGenerated() {
            return revenueGenerated;
        }

        private TopPromotion toResponse(int rank) {
            return TopPromotion.builder()
                    .rank(rank)
                    .promotionId(promotion.getPromotionId())
                    .promotionName(promotion.getName())
                    .type(promotion.getType())
                    .redemptions(redemptions)
                    .discountGiven(discountGiven)
                    .revenueGenerated(revenueGenerated)
                    .build();
        }
    }
}
