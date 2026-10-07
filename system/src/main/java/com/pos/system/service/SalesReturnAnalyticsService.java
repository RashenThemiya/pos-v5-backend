package com.pos.system.service;

import com.pos.system.dto.sale.SalesReturnAnalyticsResponse;
import com.pos.system.dto.sale.SalesReturnAnalyticsResponse.*;
import com.pos.system.model.auth.Branch;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.UnitMaster;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.model.sale.OrderProduct;
import com.pos.system.model.sale.Payment;
import com.pos.system.model.sale.SalesReturn;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.CustomerOrderRepository;
import com.pos.system.repository.ItemRepository;
import com.pos.system.repository.ItemUnitRepository;
import com.pos.system.repository.OrderProductRepository;
import com.pos.system.repository.PaymentRepository;
import com.pos.system.repository.SalesReturnRepository;
import com.pos.system.repository.UnitMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesReturnAnalyticsService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_DATE_RANGE = "30D";
    private static final String CURRENCY = "Rs";
    private static final String DEFAULT_QTY_UNIT = "units";
    private static final LocalTime DEFAULT_ACTIVITY_START = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_ACTIVITY_END = LocalTime.of(20, 0);
    private static final int DEFAULT_ACTIVITY_SLOT_MINUTES = 120;

    private final BranchRepository branchRepository;
    private final CustomerOrderRepository orderRepository;
    private final SalesReturnRepository salesReturnRepository;
    private final OrderProductRepository orderProductRepository;
    private final PaymentRepository paymentRepository;
    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final UnitMasterRepository unitMasterRepository;

    public SalesReturnAnalyticsResponse getAnalytics(Long branchId,
                                                     LocalDateTime from,
                                                     LocalDateTime to,
                                                     String dateRange,
                                                     int productLimit,
                                                     LocalTime startTime,
                                                     LocalTime endTime,
                                                     int slotMinutes) {
        Range range = resolveRange(from, to, dateRange);
        ActivityWindow activityWindow = resolveActivityWindow(startTime, endTime, slotMinutes);
        int safeProductLimit = sanitizeLimit(productLimit, 10);
        AnalyticsData data = loadData(branchId, range);

        return SalesReturnAnalyticsResponse.builder()
                .context(buildContext(data.branch()))
                .filters(AnalyticsFilters.builder()
                        .dateRange(resolveDateRangeLabel(dateRange, from, to))
                        .from(range.from())
                        .to(range.to())
                        .timezone(data.branch().getTimezone())
                        .activityStartTime(activityWindow.startTime())
                        .activityEndTime(activityWindow.endTime())
                        .activitySlotMinutes(activityWindow.slotMinutes())
                        .productLimit(safeProductLimit)
                        .build())
                .emptyState(buildEmptyState(data))
                .summary(buildSummary(data))
                .performance(buildPerformance(data))
                .customerEngagement(buildCustomerEngagement(data))
                .paymentMethods(buildPaymentMethods(data))
                .topProducts(buildTopProducts(data, safeProductLimit))
                .returnPerformance(buildReturnPerformance(data))
                .activity(buildActivity(data, activityWindow))
                .build();
    }

    public KpiSummary getSummary(Long branchId,
                                 LocalDateTime from,
                                 LocalDateTime to,
                                 String dateRange) {
        return buildSummary(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<PerformanceBucket> getPerformance(Long branchId,
                                                  LocalDateTime from,
                                                  LocalDateTime to,
                                                  String dateRange) {
        return buildPerformance(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public CustomerEngagement getCustomerEngagement(Long branchId,
                                                    LocalDateTime from,
                                                    LocalDateTime to,
                                                    String dateRange) {
        return buildCustomerEngagement(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public PaymentMethods getPaymentMethods(Long branchId,
                                            LocalDateTime from,
                                            LocalDateTime to,
                                            String dateRange) {
        return buildPaymentMethods(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public TopProducts getTopProducts(Long branchId,
                                      LocalDateTime from,
                                      LocalDateTime to,
                                      String dateRange,
                                      int limit) {
        return buildTopProducts(loadData(branchId, resolveRange(from, to, dateRange)),
                sanitizeLimit(limit, 10));
    }

    public ReturnPerformance getReturnPerformance(Long branchId,
                                                  LocalDateTime from,
                                                  LocalDateTime to,
                                                  String dateRange) {
        return buildReturnPerformance(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public ActivityResponse getActivity(Long branchId,
                                        LocalDateTime from,
                                        LocalDateTime to,
                                        String dateRange,
                                        LocalTime startTime,
                                        LocalTime endTime,
                                        int slotMinutes) {
        return buildActivity(loadData(branchId, resolveRange(from, to, dateRange)),
                resolveActivityWindow(startTime, endTime, slotMinutes));
    }

    private AnalyticsData loadData(Long branchId, Range range) {
        Branch branch = requireBranch(branchId);
        List<CustomerOrder> orders = orderRepository
                .findByBranchIdAndOrderDateBetweenOrderByOrderDateDesc(branchId, range.from(), range.to())
                .stream()
                .filter(this::isValidOrder)
                .toList();
        List<SalesReturn> returns = salesReturnRepository.findByBranchIdOrderByReturnDateDesc(branchId).stream()
                .filter(salesReturn -> isWithin(salesReturn.getReturnDate(), range.from(), range.to()))
                .filter(this::isValidReturn)
                .toList();

        Set<Long> orderIds = orders.stream()
                .map(CustomerOrder::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<OrderProduct> orderProducts = orderIds.isEmpty()
                ? List.of()
                : orderProductRepository.findByOrderIdIn(new ArrayList<>(orderIds));

        List<Payment> payments = paymentRepository.findByBranchId(branchId).stream()
                .filter(payment -> payment.getOrderId() != null && orderIds.contains(payment.getOrderId()))
                .filter(payment -> isWithin(payment.getPaymentDate(), range.from(), range.to()))
                .toList();

        Set<Long> itemIds = new HashSet<>();
        orderProducts.stream()
                .map(OrderProduct::getItemId)
                .filter(Objects::nonNull)
                .forEach(itemIds::add);

        Map<Long, Item> itemsById = new HashMap<>();
        itemRepository.findAllById(itemIds)
                .forEach(item -> itemsById.put(item.getItemId(), item));

        Set<Long> unitIds = orderProducts.stream()
                .map(OrderProduct::getUnitId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ItemUnit> itemUnitsById = new HashMap<>();
        itemUnitRepository.findAllById(unitIds)
                .forEach(unit -> itemUnitsById.put(unit.getUnitId(), unit));

        Set<Long> masterUnitIds = itemUnitsById.values().stream()
                .map(ItemUnit::getMasterUnitId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UnitMaster> masterUnitsById = new HashMap<>();
        unitMasterRepository.findAllById(masterUnitIds)
                .forEach(unit -> masterUnitsById.put(unit.getUnitId(), unit));

        return new AnalyticsData(
                branch,
                range,
                orders,
                returns,
                orderProducts,
                payments,
                itemsById,
                itemUnitsById,
                masterUnitsById);
    }

    private PageContext buildContext(Branch branch) {
        return PageContext.builder()
                .branchId(branch.getBranchId())
                .branchName(branch.getName())
                .title("Sales and Return Analytics")
                .subtitle("Performance and activity overview")
                .backRoute("/analytics")
                .tabs(List.of("Overview", "Customers & Payments", "Top Products", "Returns"))
                .build();
    }

    private EmptyState buildEmptyState(AnalyticsData data) {
        boolean empty = data.orders().isEmpty() && data.returns().isEmpty();
        return EmptyState.builder()
                .empty(empty)
                .message(empty ? "No sales or return data is available for the selected period." : null)
                .build();
    }

    private KpiSummary buildSummary(AnalyticsData data) {
        BigDecimal salesAmount = sumOrderTotals(data.orders());
        BigDecimal returnAmount = sumRefunds(data.returns());

        return KpiSummary.builder()
                .totalSales(data.orders().size())
                .totalReturns(data.returns().size())
                .grossSalesAmount(salesAmount)
                .returnAmount(returnAmount)
                .netRevenue(salesAmount.subtract(returnAmount))
                .currency(CURRENCY)
                .build();
    }

    private List<PerformanceBucket> buildPerformance(AnalyticsData data) {
        List<PerformanceAccumulator> buckets = createPerformanceBuckets(data.range().from(), data.range().to());
        for (CustomerOrder order : data.orders()) {
            bucketFor(buckets, order.getOrderDate())
                    .ifPresent(bucket -> bucket.addSale(order));
        }
        for (SalesReturn salesReturn : data.returns()) {
            bucketFor(buckets, salesReturn.getReturnDate())
                    .ifPresent(bucket -> bucket.addReturn(salesReturn));
        }
        return buckets.stream()
                .map(PerformanceAccumulator::toResponse)
                .toList();
    }

    private CustomerEngagement buildCustomerEngagement(AnalyticsData data) {
        long walkIn = data.orders().stream()
                .filter(order -> order.getCustomerId() == null)
                .count();
        long registered = data.orders().size() - walkIn;
        BigDecimal total = BigDecimal.valueOf(data.orders().size());

        List<CustomerSegment> segments = List.of(
                CustomerSegment.builder()
                        .type("Walk-in")
                        .count(walkIn)
                        .percentage(percent(BigDecimal.valueOf(walkIn), total))
                        .build(),
                CustomerSegment.builder()
                        .type("Registered")
                        .count(registered)
                        .percentage(percent(BigDecimal.valueOf(registered), total))
                        .build());

        return CustomerEngagement.builder()
                .totalTransactions(data.orders().size())
                .segments(segments)
                .build();
    }

    private PaymentMethods buildPaymentMethods(AnalyticsData data) {
        BigDecimal totalAmount = data.payments().stream()
                .map(payment -> nvl(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PaymentMethodEntry> methods = data.payments().stream()
                .collect(Collectors.groupingBy(payment -> normalizePaymentMethod(payment.getPaymentMethod())))
                .entrySet()
                .stream()
                .map(entry -> {
                    BigDecimal amount = entry.getValue().stream()
                            .map(payment -> nvl(payment.getAmount()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return PaymentMethodEntry.builder()
                            .method(entry.getKey())
                            .amount(amount)
                            .percentage(percent(amount, totalAmount))
                            .transactionCount(entry.getValue().size())
                            .build();
                })
                .sorted(Comparator.comparing(PaymentMethodEntry::getAmount).reversed())
                .toList();

        return PaymentMethods.builder()
                .totalAmount(totalAmount)
                .currency(CURRENCY)
                .methods(methods)
                .build();
    }

    private TopProducts buildTopProducts(AnalyticsData data, int limit) {
        Map<Long, ProductAccumulator> products = new HashMap<>();
        for (OrderProduct product : data.orderProducts()) {
            products.computeIfAbsent(product.getItemId(), ProductAccumulator::new)
                    .add(product, unitName(data, product.getUnitId()));
        }

        List<ProductAccumulator> ranked = products.values().stream()
                .sorted(Comparator.comparing(ProductAccumulator::quantity).reversed()
                        .thenComparing(ProductAccumulator::revenue, Comparator.reverseOrder()))
                .limit(limit)
                .toList();

        List<TopProductEntry> entries = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            ProductAccumulator accumulator = ranked.get(index);
            Item item = data.itemsById().get(accumulator.itemId);
            entries.add(accumulator.toResponse(index + 1, item));
        }
        return TopProducts.builder().items(entries).build();
    }

    private ReturnPerformance buildReturnPerformance(AnalyticsData data) {
        BigDecimal refundAmount = sumRefunds(data.returns());
        long returnedOrders = data.returns().size();
        BigDecimal averageRefund = returnedOrders == 0
                ? BigDecimal.ZERO
                : refundAmount.divide(BigDecimal.valueOf(returnedOrders), 2, RoundingMode.HALF_UP);

        return ReturnPerformance.builder()
                .returnedOrders(returnedOrders)
                .refundAmount(refundAmount)
                .averageRefundPerReturn(averageRefund)
                .refundRate(percent(BigDecimal.valueOf(returnedOrders), BigDecimal.valueOf(data.orders().size())))
                .currency(CURRENCY)
                .build();
    }

    private ActivityResponse buildActivity(AnalyticsData data, ActivityWindow activityWindow) {
        List<ActivityAccumulator> buckets = createActivityBuckets(activityWindow);
        for (CustomerOrder order : data.orders()) {
            activityBucketFor(buckets, order.getOrderDate())
                    .ifPresent(bucket -> bucket.addSale(order));
        }
        for (SalesReturn salesReturn : data.returns()) {
            activityBucketFor(buckets, salesReturn.getReturnDate())
                    .ifPresent(bucket -> bucket.addReturn(salesReturn));
        }

        return ActivityResponse.builder()
                .startTime(activityWindow.startTime())
                .endTime(activityWindow.endTime())
                .slotMinutes(activityWindow.slotMinutes())
                .buckets(buckets.stream().map(ActivityAccumulator::toResponse).toList())
                .peaks(List.of(
                        activityPeak("SALES", buckets, true),
                        activityPeak("RETURNS", buckets, false)))
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

    private ActivityWindow resolveActivityWindow(LocalTime startTime, LocalTime endTime, int slotMinutes) {
        LocalTime resolvedStart = startTime != null ? startTime : DEFAULT_ACTIVITY_START;
        LocalTime resolvedEnd = endTime != null ? endTime : DEFAULT_ACTIVITY_END;
        if (!resolvedEnd.isAfter(resolvedStart)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        int resolvedSlotMinutes = slotMinutes > 0 ? slotMinutes : DEFAULT_ACTIVITY_SLOT_MINUTES;
        resolvedSlotMinutes = Math.min(Math.max(resolvedSlotMinutes, 15), 240);
        return new ActivityWindow(resolvedStart, resolvedEnd, resolvedSlotMinutes);
    }

    private void validateDateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("to must be after from");
        }
    }

    private boolean isValidOrder(CustomerOrder order) {
        return "COMPLETED".equalsIgnoreCase(safe(order.getStatus()));
    }

    private boolean isValidReturn(SalesReturn salesReturn) {
        String status = normalizeStatus(salesReturn.getStatus(), "");
        return !"CANCELLED".equals(status)
                && !"CANCELED".equals(status)
                && !"VOID".equals(status)
                && !"VOIDED".equals(status)
                && !"DRAFT".equals(status)
                && !"INVALID".equals(status);
    }

    private boolean isWithin(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        return value != null && !value.isBefore(from) && !value.isAfter(to);
    }

    private BigDecimal sumOrderTotals(List<CustomerOrder> orders) {
        return orders.stream()
                .map(order -> nvl(order.getTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumRefunds(List<SalesReturn> returns) {
        return returns.stream()
                .map(salesReturn -> nvl(salesReturn.getRefundAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<PerformanceAccumulator> createPerformanceBuckets(LocalDateTime from, LocalDateTime to) {
        long dayCount = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()) + 1;
        if (dayCount <= 31) {
            return createDailyBuckets(from, to);
        }
        if (dayCount <= 92) {
            return createWeeklyBuckets(from, to);
        }
        return createMonthlyBuckets(from, to);
    }

    private List<PerformanceAccumulator> createDailyBuckets(LocalDateTime from, LocalDateTime to) {
        List<PerformanceAccumulator> buckets = new ArrayList<>();
        int index = 1;
        for (LocalDate date = from.toLocalDate(); !date.isAfter(to.toLocalDate()); date = date.plusDays(1)) {
            LocalDateTime bucketFrom = date.atStartOfDay().isBefore(from) ? from : date.atStartOfDay();
            LocalDateTime bucketTo = date.plusDays(1).atStartOfDay().minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new PerformanceAccumulator(date.toString(), DAY_LABEL.format(date), bucketFrom, bucketTo, index++));
        }
        return buckets;
    }

    private List<PerformanceAccumulator> createWeeklyBuckets(LocalDateTime from, LocalDateTime to) {
        List<PerformanceAccumulator> buckets = new ArrayList<>();
        LocalDateTime cursor = from;
        int index = 1;
        while (!cursor.isAfter(to)) {
            LocalDateTime bucketTo = cursor.plusDays(7).minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new PerformanceAccumulator("week-" + index, "Week " + index, cursor, bucketTo, index));
            cursor = bucketTo.plusNanos(1);
            index++;
        }
        return buckets;
    }

    private List<PerformanceAccumulator> createMonthlyBuckets(LocalDateTime from, LocalDateTime to) {
        List<PerformanceAccumulator> buckets = new ArrayList<>();
        LocalDateTime cursor = from;
        int index = 1;
        while (!cursor.isAfter(to)) {
            LocalDateTime nextMonth = cursor.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay();
            LocalDateTime bucketTo = nextMonth.minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new PerformanceAccumulator(
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

    private Optional<PerformanceAccumulator> bucketFor(List<PerformanceAccumulator> buckets, LocalDateTime value) {
        return buckets.stream()
                .filter(bucket -> isWithin(value, bucket.from, bucket.to))
                .findFirst();
    }

    private List<ActivityAccumulator> createActivityBuckets(ActivityWindow window) {
        List<ActivityAccumulator> buckets = new ArrayList<>();
        LocalTime cursor = window.startTime();
        int index = 1;
        while (cursor.isBefore(window.endTime())) {
            LocalTime bucketEnd = cursor.plusMinutes(window.slotMinutes());
            if (bucketEnd.isAfter(window.endTime())) {
                bucketEnd = window.endTime();
            }
            buckets.add(new ActivityAccumulator(
                    TIME_LABEL.format(cursor) + "-" + TIME_LABEL.format(bucketEnd),
                    TIME_LABEL.format(cursor) + "-" + TIME_LABEL.format(bucketEnd),
                    cursor,
                    bucketEnd,
                    index++));
            cursor = bucketEnd;
        }
        if (!buckets.isEmpty()) {
            buckets.get(buckets.size() - 1).last = true;
        }
        return buckets;
    }

    private Optional<ActivityAccumulator> activityBucketFor(List<ActivityAccumulator> buckets, LocalDateTime value) {
        if (value == null) {
            return Optional.empty();
        }
        LocalTime time = value.toLocalTime();
        return buckets.stream()
                .filter(bucket -> !time.isBefore(bucket.startTime)
                        && (time.isBefore(bucket.endTime) || time.equals(bucket.endTime) && bucket.last))
                .findFirst();
    }

    private ActivityPeak activityPeak(String metric, List<ActivityAccumulator> buckets, boolean sales) {
        ActivityAccumulator bucket = buckets.stream()
                .max(Comparator.comparing(bucketAccumulator ->
                        sales ? bucketAccumulator.salesAmount : bucketAccumulator.returnAmount))
                .orElse(null);
        if (bucket == null) {
            return ActivityPeak.builder()
                    .metric(metric)
                    .amount(BigDecimal.ZERO)
                    .count(0)
                    .build();
        }

        BigDecimal amount = sales ? bucket.salesAmount : bucket.returnAmount;
        long count = sales ? bucket.salesCount : bucket.returnCount;
        if (amount.compareTo(BigDecimal.ZERO) <= 0 && count == 0) {
            return ActivityPeak.builder()
                    .metric(metric)
                    .amount(BigDecimal.ZERO)
                    .count(0)
                    .build();
        }
        return ActivityPeak.builder()
                .metric(metric)
                .bucketKey(bucket.key)
                .label(bucket.label)
                .amount(amount)
                .count(count)
                .build();
    }

    private String unitName(AnalyticsData data, Long unitId) {
        ItemUnit unit = unitId != null ? data.itemUnitsById().get(unitId) : null;
        if (unit == null) {
            return null;
        }
        UnitMaster masterUnit = unit.getMasterUnitId() != null
                ? data.masterUnitsById().get(unit.getMasterUnitId())
                : null;
        return masterUnit != null ? masterUnit.getName() : unit.getUnitName();
    }

    private String normalizePaymentMethod(String method) {
        if (method == null || method.isBlank()) {
            return "UNKNOWN";
        }
        return method.trim().replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        return status.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return nvl(numerator).multiply(BigDecimal.valueOf(100))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private int sanitizeLimit(int limit, int fallback) {
        int value = limit > 0 ? limit : fallback;
        return Math.min(value, 50);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record Range(LocalDateTime from, LocalDateTime to) {
    }

    private record ActivityWindow(LocalTime startTime, LocalTime endTime, int slotMinutes) {
    }

    private record AnalyticsData(Branch branch,
                                 Range range,
                                 List<CustomerOrder> orders,
                                 List<SalesReturn> returns,
                                 List<OrderProduct> orderProducts,
                                 List<Payment> payments,
                                 Map<Long, Item> itemsById,
                                 Map<Long, ItemUnit> itemUnitsById,
                                 Map<Long, UnitMaster> masterUnitsById) {
    }

    private static class PerformanceAccumulator {
        private final String key;
        private final String label;
        private final LocalDateTime from;
        private final LocalDateTime to;
        private final int index;
        private long salesCount;
        private long returnCount;
        private BigDecimal salesAmount = BigDecimal.ZERO;
        private BigDecimal returnAmount = BigDecimal.ZERO;

        private PerformanceAccumulator(String key, String label, LocalDateTime from, LocalDateTime to, int index) {
            this.key = key;
            this.label = label;
            this.from = from;
            this.to = to;
            this.index = index;
        }

        private void addSale(CustomerOrder order) {
            salesCount++;
            salesAmount = salesAmount.add(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
        }

        private void addReturn(SalesReturn salesReturn) {
            returnCount++;
            returnAmount = returnAmount.add(salesReturn.getRefundAmount() != null
                    ? salesReturn.getRefundAmount()
                    : BigDecimal.ZERO);
        }

        private PerformanceBucket toResponse() {
            return PerformanceBucket.builder()
                    .bucketKey(key != null ? key : "bucket-" + index)
                    .label(label)
                    .bucketFrom(from)
                    .bucketTo(to)
                    .salesCount(salesCount)
                    .returnCount(returnCount)
                    .salesAmount(salesAmount)
                    .returnAmount(returnAmount)
                    .netAmount(salesAmount.subtract(returnAmount))
                    .build();
        }
    }

    private static class ActivityAccumulator {
        private final String key;
        private final String label;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final int index;
        private boolean last;
        private long salesCount;
        private long returnCount;
        private BigDecimal salesAmount = BigDecimal.ZERO;
        private BigDecimal returnAmount = BigDecimal.ZERO;

        private ActivityAccumulator(String key, String label, LocalTime startTime, LocalTime endTime, int index) {
            this.key = key;
            this.label = label;
            this.startTime = startTime;
            this.endTime = endTime;
            this.index = index;
        }

        private void addSale(CustomerOrder order) {
            salesCount++;
            salesAmount = salesAmount.add(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
        }

        private void addReturn(SalesReturn salesReturn) {
            returnCount++;
            returnAmount = returnAmount.add(salesReturn.getRefundAmount() != null
                    ? salesReturn.getRefundAmount()
                    : BigDecimal.ZERO);
        }

        private ActivityBucket toResponse() {
            return ActivityBucket.builder()
                    .bucketKey(key != null ? key : "slot-" + index)
                    .label(label)
                    .startTime(startTime)
                    .endTime(endTime)
                    .salesCount(salesCount)
                    .returnCount(returnCount)
                    .salesAmount(salesAmount)
                    .returnAmount(returnAmount)
                    .build();
        }
    }

    private static class ProductAccumulator {
        private final Long itemId;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal revenue = BigDecimal.ZERO;
        private final Map<String, Long> unitCounts = new HashMap<>();

        private ProductAccumulator(Long itemId) {
            this.itemId = itemId;
        }

        private void add(OrderProduct product, String unitName) {
            quantity = quantity.add(product.getQuantity() != null ? product.getQuantity() : BigDecimal.ZERO);
            revenue = revenue.add(product.getLineTotal() != null ? product.getLineTotal() : BigDecimal.ZERO);
            if (unitName != null && !unitName.isBlank()) {
                unitCounts.merge(unitName, 1L, Long::sum);
            }
        }

        private BigDecimal quantity() {
            return quantity;
        }

        private BigDecimal revenue() {
            return revenue;
        }

        private TopProductEntry toResponse(int rank, Item item) {
            return TopProductEntry.builder()
                    .rank(rank)
                    .productId(itemId)
                    .productName(item != null ? item.getName() : "Unknown Product")
                    .sku(item != null ? item.getSku() : null)
                    .quantity(quantity)
                    .unitName(mostFrequent(unitCounts, DEFAULT_QTY_UNIT))
                    .revenue(revenue)
                    .build();
        }

        private static String mostFrequent(Map<String, Long> counts, String fallback) {
            return counts.entrySet().stream()
                    .max(Map.Entry.<String, Long>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey)
                    .orElse(fallback);
        }
    }
}
