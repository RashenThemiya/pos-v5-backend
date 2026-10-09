package com.pos.system.service;

import com.pos.system.dto.cash.CounterAnalyticsResponse;
import com.pos.system.dto.cash.CounterAnalyticsResponse.*;
import com.pos.system.model.auth.Branch;
import com.pos.system.model.cash.CashSession;
import com.pos.system.model.cash.CashSessionTransaction;
import com.pos.system.model.cash.Counter;
import com.pos.system.model.cash.CounterVarianceAlertRead;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.model.sale.SalesReturn;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.CashSessionRepository;
import com.pos.system.repository.CashSessionTransactionRepository;
import com.pos.system.repository.CounterRepository;
import com.pos.system.repository.CounterVarianceAlertReadRepository;
import com.pos.system.repository.CustomerOrderRepository;
import com.pos.system.repository.SalesReturnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CounterAnalyticsService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_DATE_RANGE = "30D";
    private static final String CURRENCY = "Rs";
    private static final BigDecimal CRITICAL_VARIANCE_AMOUNT = BigDecimal.valueOf(1000);

    private final BranchRepository branchRepository;
    private final CounterRepository counterRepository;
    private final CashSessionRepository cashSessionRepository;
    private final CashSessionTransactionRepository transactionRepository;
    private final CustomerOrderRepository orderRepository;
    private final SalesReturnRepository salesReturnRepository;
    private final CounterVarianceAlertReadRepository alertReadRepository;

    public CounterAnalyticsResponse getAnalytics(Long branchId,
                                                 LocalDateTime from,
                                                 LocalDateTime to,
                                                 String dateRange,
                                                 LocalDate trendDate) {
        Range range = resolveRange(from, to, dateRange);
        AnalyticsData data = loadData(branchId, range);
        LocalDate resolvedTrendDate = trendDate != null ? trendDate : range.to().toLocalDate();
        List<VarianceAlert> alerts = buildVarianceAlerts(data);

        return CounterAnalyticsResponse.builder()
                .context(buildContext(data.branch()))
                .filters(AnalyticsFilters.builder()
                        .dateRange(resolveDateRangeLabel(dateRange, from, to))
                        .from(range.from())
                        .to(range.to())
                        .timezone(data.branch().getTimezone())
                        .trendDate(resolvedTrendDate)
                        .build())
                .emptyState(buildEmptyState(data))
                .summary(buildSummary(data, loadData(branchId, previousRange(range))))
                .unreadAlertCount(unreadCount(alerts))
                .varianceAlerts(alerts)
                .salesPerformance(buildSalesPerformance(data))
                .counterPerformance(buildCounterPerformance(data))
                .cashFlow(buildCashFlow(data))
                .salesTrend(buildSalesTrend(branchId, resolvedTrendDate))
                .counterUsage(buildCounterUsage(data))
                .build();
    }

    public CounterSummary getSummary(Long branchId,
                                     LocalDateTime from,
                                     LocalDateTime to,
                                     String dateRange) {
        Range range = resolveRange(from, to, dateRange);
        return buildSummary(loadData(branchId, range), loadData(branchId, previousRange(range)));
    }

    public List<SalesPerformanceBucket> getSalesPerformance(Long branchId,
                                                            LocalDateTime from,
                                                            LocalDateTime to,
                                                            String dateRange) {
        return buildSalesPerformance(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<CounterPerformanceEntry> getCounterPerformance(Long branchId,
                                                               LocalDateTime from,
                                                               LocalDateTime to,
                                                               String dateRange) {
        return buildCounterPerformance(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public CashFlow getCashFlow(Long branchId,
                                LocalDateTime from,
                                LocalDateTime to,
                                String dateRange) {
        return buildCashFlow(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<HourlySalesTrend> getSalesTrend(Long branchId, LocalDate trendDate) {
        return buildSalesTrend(branchId, trendDate != null ? trendDate : LocalDate.now());
    }

    public List<CounterUsageEntry> getCounterUsage(Long branchId,
                                                   LocalDateTime from,
                                                   LocalDateTime to,
                                                   String dateRange) {
        return buildCounterUsage(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<VarianceAlert> getVarianceAlerts(Long branchId,
                                                 LocalDateTime from,
                                                 LocalDateTime to,
                                                 String dateRange) {
        return buildVarianceAlerts(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    @Transactional
    public NotificationReadResponse markAllVarianceAlertsRead(Long branchId,
                                                              LocalDateTime from,
                                                              LocalDateTime to,
                                                              String dateRange) {
        AnalyticsData data = loadData(branchId, resolveRange(from, to, dateRange));
        List<VarianceAlert> current = buildVarianceAlerts(data);
        long markedRead = 0;
        LocalDateTime now = LocalDateTime.now();
        for (VarianceAlert alert : current) {
            if (!alert.isRead() && !alertReadRepository.existsByBranchIdAndAlertId(branchId, alert.getId())) {
                CounterVarianceAlertRead read = new CounterVarianceAlertRead();
                read.setBranchId(branchId);
                read.setAlertId(alert.getId());
                read.setReadAt(now);
                alertReadRepository.save(read);
                markedRead++;
            }
        }
        List<VarianceAlert> updated = buildVarianceAlerts(data);
        return NotificationReadResponse.builder()
                .markedRead(markedRead)
                .unreadAlertCount(unreadCount(updated))
                .varianceAlerts(updated)
                .build();
    }

    private AnalyticsData loadData(Long branchId, Range range) {
        Branch branch = requireBranch(branchId);
        List<Counter> counters = counterRepository.findByBranchId(branchId).stream()
                .sorted(counterComparator())
                .toList();
        List<Long> counterIds = counters.stream()
                .map(Counter::getCounterId)
                .filter(Objects::nonNull)
                .toList();

        List<CustomerOrder> orders = orderRepository
                .findByBranchIdAndOrderDateBetweenOrderByOrderDateDesc(branchId, range.from(), range.to())
                .stream()
                .filter(this::isValidOrder)
                .toList();
        List<SalesReturn> returns = salesReturnRepository
                .findByBranchIdAndReturnDateBetweenOrderByReturnDateDesc(branchId, range.from(), range.to())
                .stream()
                .filter(this::isValidReturn)
                .toList();

        Map<Long, CustomerOrder> ordersById = new HashMap<>();
        orders.forEach(order -> ordersById.put(order.getOrderId(), order));
        Set<Long> missingReturnOrderIds = returns.stream()
                .map(SalesReturn::getOrderId)
                .filter(Objects::nonNull)
                .filter(orderId -> !ordersById.containsKey(orderId))
                .collect(Collectors.toSet());
        if (!missingReturnOrderIds.isEmpty()) {
            orderRepository.findAllById(missingReturnOrderIds)
                    .forEach(order -> ordersById.put(order.getOrderId(), order));
        }

        List<CashSession> openedSessions = counterIds.isEmpty()
                ? List.of()
                : cashSessionRepository.findByCounterIdInAndOpenedAtBetweenOrderByOpenedAtDesc(
                counterIds, range.from(), range.to());
        List<CashSession> closedSessions = counterIds.isEmpty()
                ? List.of()
                : cashSessionRepository.findByCounterIdInAndClosedAtBetweenOrderByClosedAtDesc(
                counterIds, range.from(), range.to());
        List<CashSessionTransaction> transactions = counterIds.isEmpty()
                ? List.of()
                : transactionRepository.findByCounterIdsAndCreatedAtBetween(counterIds, range.from(), range.to());

        Map<Long, CashSession> sessionsById = new HashMap<>();
        openedSessions.forEach(session -> sessionsById.put(session.getSessionId(), session));
        closedSessions.forEach(session -> sessionsById.put(session.getSessionId(), session));
        Set<Long> orderSessionIds = ordersById.values().stream()
                .map(CustomerOrder::getCashSessionId)
                .filter(Objects::nonNull)
                .filter(sessionId -> !sessionsById.containsKey(sessionId))
                .collect(Collectors.toSet());
        if (!orderSessionIds.isEmpty()) {
            cashSessionRepository.findAllById(orderSessionIds)
                    .forEach(session -> sessionsById.put(session.getSessionId(), session));
        }

        Map<Long, Counter> countersById = counters.stream()
                .collect(Collectors.toMap(Counter::getCounterId, counter -> counter, (left, right) -> left));

        return new AnalyticsData(
                branch,
                range,
                counters,
                countersById,
                orders,
                returns,
                ordersById,
                openedSessions,
                closedSessions,
                transactions,
                sessionsById);
    }

    private PageContext buildContext(Branch branch) {
        return PageContext.builder()
                .branchId(branch.getBranchId())
                .branchName(branch.getName())
                .title("Counter Analytics")
                .description("Monitor counter performance, sales, and operational insights.")
                .backRoute("/analytics")
                .tabs(List.of("Overview", "Performance", "Utilization", "Cash Flow"))
                .build();
    }

    private EmptyState buildEmptyState(AnalyticsData data) {
        boolean empty = data.orders().isEmpty()
                && data.returns().isEmpty()
                && data.openedSessions().isEmpty()
                && data.transactions().isEmpty();
        return EmptyState.builder()
                .empty(empty)
                .message(empty ? "No counter activity is available for the selected period." : null)
                .build();
    }

    private CounterSummary buildSummary(AnalyticsData current, AnalyticsData previous) {
        BigDecimal currentNetSales = netSales(current);
        BigDecimal previousNetSales = netSales(previous);
        long currentTransactions = current.orders().size();
        long previousTransactions = previous.orders().size();
        BigDecimal currentAverage = average(currentNetSales, currentTransactions);
        BigDecimal previousAverage = average(previousNetSales, previousTransactions);

        return CounterSummary.builder()
                .netSales(currentNetSales)
                .netSalesChangePercentage(growthPercent(currentNetSales, previousNetSales))
                .transactions(currentTransactions)
                .transactionsChangePercentage(growthPercent(
                        BigDecimal.valueOf(currentTransactions),
                        BigDecimal.valueOf(previousTransactions)))
                .averageTransaction(currentAverage)
                .averageTransactionChangePercentage(growthPercent(currentAverage, previousAverage))
                .cashSessions(current.openedSessions().size())
                .cashSessionsChangePercentage(growthPercent(
                        BigDecimal.valueOf(current.openedSessions().size()),
                        BigDecimal.valueOf(previous.openedSessions().size())))
                .currency(CURRENCY)
                .build();
    }

    private List<SalesPerformanceBucket> buildSalesPerformance(AnalyticsData data) {
        List<PerformanceAccumulator> buckets = createPerformanceBuckets(data.range().from(), data.range().to());
        for (CustomerOrder order : data.orders()) {
            bucketFor(buckets, order.getOrderDate())
                    .ifPresent(bucket -> bucket.addRevenue(nvl(order.getTotal()), 1));
        }
        for (SalesReturn salesReturn : data.returns()) {
            bucketFor(buckets, salesReturn.getReturnDate())
                    .ifPresent(bucket -> bucket.subtractRevenue(nvl(salesReturn.getRefundAmount())));
        }
        return buckets.stream()
                .map(PerformanceAccumulator::toResponse)
                .toList();
    }

    private List<CounterPerformanceEntry> buildCounterPerformance(AnalyticsData data) {
        Map<Long, CounterAccumulator> byCounter = counterAccumulators(data.counters());
        for (CustomerOrder order : data.orders()) {
            resolveCounterIdForOrder(data, order)
                    .ifPresent(counterId -> byCounter.computeIfAbsent(counterId, CounterAccumulator::new)
                            .addRevenue(nvl(order.getTotal()), 1));
        }
        for (SalesReturn salesReturn : data.returns()) {
            CustomerOrder order = data.ordersById().get(salesReturn.getOrderId());
            if (order == null) {
                continue;
            }
            resolveCounterIdForOrder(data, order)
                    .ifPresent(counterId -> byCounter.computeIfAbsent(counterId, CounterAccumulator::new)
                            .subtractRevenue(nvl(salesReturn.getRefundAmount())));
        }

        BigDecimal maxRevenue = byCounter.values().stream()
                .map(CounterAccumulator::revenue)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        long maxTransactions = byCounter.values().stream()
                .mapToLong(CounterAccumulator::transactions)
                .max()
                .orElse(0);

        List<CounterPerformanceEntry> rows = new ArrayList<>();
        List<CounterAccumulator> sorted = byCounter.values().stream()
                .sorted(Comparator.comparing(accumulator -> counterSortName(data, accumulator.counterId)))
                .toList();
        for (int index = 0; index < sorted.size(); index++) {
            CounterAccumulator accumulator = sorted.get(index);
            Counter counter = data.countersById().get(accumulator.counterId);
            rows.add(accumulator.toPerformanceResponse(index + 1, counter, maxRevenue, maxTransactions));
        }
        return rows;
    }

    private CashFlow buildCashFlow(AnalyticsData data) {
        BigDecimal cashIn = BigDecimal.ZERO;
        BigDecimal cashOut = BigDecimal.ZERO;
        for (CashSessionTransaction transaction : data.transactions()) {
            BigDecimal amount = nvl(transaction.getAmount());
            if (isCashIn(transaction)) {
                cashIn = cashIn.add(amount);
            } else if (isCashOut(transaction)) {
                cashOut = cashOut.add(amount);
            }
        }
        return CashFlow.builder()
                .cashIn(cashIn)
                .cashOut(cashOut)
                .netCashFlow(cashIn.subtract(cashOut))
                .currency(CURRENCY)
                .build();
    }

    private List<HourlySalesTrend> buildSalesTrend(Long branchId, LocalDate trendDate) {
        LocalDate date = trendDate != null ? trendDate : LocalDate.now();
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay().minusNanos(1);
        List<CustomerOrder> orders = orderRepository
                .findByBranchIdAndOrderDateBetweenOrderByOrderDateDesc(branchId, from, to)
                .stream()
                .filter(this::isValidOrder)
                .toList();
        List<SalesReturn> returns = salesReturnRepository
                .findByBranchIdAndReturnDateBetweenOrderByReturnDateDesc(branchId, from, to)
                .stream()
                .filter(this::isValidReturn)
                .toList();

        List<HourlyAccumulator> buckets = createHourlyBuckets(date);
        for (CustomerOrder order : orders) {
            hourlyBucketFor(buckets, order.getOrderDate())
                    .ifPresent(bucket -> bucket.addRevenue(nvl(order.getTotal()), 1));
        }
        for (SalesReturn salesReturn : returns) {
            hourlyBucketFor(buckets, salesReturn.getReturnDate())
                    .ifPresent(bucket -> bucket.subtractRevenue(nvl(salesReturn.getRefundAmount())));
        }

        BigDecimal maxSales = buckets.stream()
                .map(HourlyAccumulator::actualSales)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        return buckets.stream()
                .map(bucket -> bucket.toResponse(maxSales))
                .toList();
    }

    private List<CounterUsageEntry> buildCounterUsage(AnalyticsData data) {
        Map<Long, CounterAccumulator> byCounter = counterAccumulators(data.counters());
        for (CustomerOrder order : data.orders()) {
            resolveCounterIdForOrder(data, order)
                    .ifPresent(counterId -> byCounter.computeIfAbsent(counterId, CounterAccumulator::new)
                            .addTransactions(1));
        }
        BigDecimal totalTransactions = BigDecimal.valueOf(data.orders().size());
        return byCounter.values().stream()
                .sorted(Comparator.comparing(accumulator -> counterSortName(data, accumulator.counterId)))
                .map(accumulator -> {
                    Counter counter = data.countersById().get(accumulator.counterId);
                    return CounterUsageEntry.builder()
                            .counterId(accumulator.counterId)
                            .counterName(counter != null ? counter.getName() : "Unknown Counter")
                            .transactions(accumulator.transactions())
                            .usagePercentage(percent(BigDecimal.valueOf(accumulator.transactions()), totalTransactions))
                            .build();
                })
                .toList();
    }

    private List<VarianceAlert> buildVarianceAlerts(AnalyticsData data) {
        List<CashSession> varianceSessions = data.closedSessions().stream()
                .filter(session -> nvl(session.getCashDifference()).compareTo(BigDecimal.ZERO) != 0)
                .sorted(Comparator.comparing(CashSession::getClosedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Set<String> alertIds = varianceSessions.stream()
                .map(session -> alertId(session.getSessionId()))
                .collect(Collectors.toSet());
        Set<String> readIds = alertIds.isEmpty()
                ? Set.of()
                : alertReadRepository.findByBranchIdAndAlertIdIn(data.branch().getBranchId(), alertIds).stream()
                .map(CounterVarianceAlertRead::getAlertId)
                .collect(Collectors.toSet());

        return varianceSessions.stream()
                .map(session -> buildVarianceAlert(data, session, readIds.contains(alertId(session.getSessionId()))))
                .toList();
    }

    private VarianceAlert buildVarianceAlert(AnalyticsData data, CashSession session, boolean read) {
        Counter counter = data.countersById().get(session.getCounterId());
        BigDecimal expected = nvl(session.getExpectedCash());
        BigDecimal actual = nvl(session.getClosingCash());
        BigDecimal variance = nvl(session.getCashDifference());
        String severity = variance.abs().compareTo(CRITICAL_VARIANCE_AMOUNT) >= 0 ? "CRITICAL" : "WARNING";
        String type = variance.compareTo(BigDecimal.ZERO) < 0 ? "CASH_VARIANCE" : "REVIEW_REQUIRED";
        String counterName = counter != null ? counter.getName() : "Unknown Counter";
        String message = "Expected " + CURRENCY + " " + expected
                + ", actual " + CURRENCY + " " + actual
                + ", variance " + CURRENCY + " " + variance + ".";

        return VarianceAlert.builder()
                .id(alertId(session.getSessionId()))
                .type(type)
                .severity(severity)
                .title(type.equals("CASH_VARIANCE") ? "Cash variance detected" : "Review required")
                .message(message)
                .read(read)
                .createdAt(session.getClosedAt())
                .sessionId(session.getSessionId())
                .counterId(session.getCounterId())
                .counterName(counterName)
                .expectedAmount(expected)
                .actualAmount(actual)
                .variance(variance)
                .currency(CURRENCY)
                .targetRoute("/cash/sessions/" + session.getSessionId())
                .build();
    }

    private Map<Long, CounterAccumulator> counterAccumulators(List<Counter> counters) {
        Map<Long, CounterAccumulator> byCounter = new LinkedHashMap<>();
        counters.forEach(counter -> byCounter.put(counter.getCounterId(), new CounterAccumulator(counter.getCounterId())));
        return byCounter;
    }

    private Optional<Long> resolveCounterIdForOrder(AnalyticsData data, CustomerOrder order) {
        if (order.getCashSessionId() == null) {
            return Optional.empty();
        }
        CashSession session = data.sessionsById().get(order.getCashSessionId());
        return session != null ? Optional.ofNullable(session.getCounterId()) : Optional.empty();
    }

    private String counterSortName(AnalyticsData data, Long counterId) {
        Counter counter = data.countersById().get(counterId);
        String name = counter != null ? counter.getName() : null;
        return (name == null || name.isBlank() ? "zz-" + counterId : name).toLowerCase(Locale.ROOT);
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

    private Range previousRange(Range range) {
        Duration duration = Duration.between(range.from(), range.to());
        LocalDateTime previousTo = range.from().minusNanos(1);
        return new Range(previousTo.minus(duration), previousTo);
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
            case "7D", "30D", "3M", "6M", "CUSTOM" -> normalized;
            default -> DEFAULT_DATE_RANGE;
        };
    }

    private void validateDateRange(LocalDateTime from, LocalDateTime to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be after or equal to from");
        }
    }

    private boolean isValidOrder(CustomerOrder order) {
        return "COMPLETED".equalsIgnoreCase(safe(order.getStatus()));
    }

    private boolean isValidReturn(SalesReturn salesReturn) {
        String status = normalize(salesReturn.getStatus());
        return !"CANCELLED".equals(status)
                && !"CANCELED".equals(status)
                && !"VOID".equals(status)
                && !"VOIDED".equals(status)
                && !"DRAFT".equals(status)
                && !"INVALID".equals(status);
    }

    private boolean isCashIn(CashSessionTransaction transaction) {
        String type = normalize(transaction.getType());
        if ("SALE".equals(type)) {
            return isCashPayment(transaction.getPaymentMethod());
        }
        return Set.of("CUSTOMER_PAYMENT_IN", "SUPPLIER_REFUND_IN", "PURCHASE_RETURN_CASH_REFUND").contains(type);
    }

    private boolean isCashOut(CashSessionTransaction transaction) {
        String type = normalize(transaction.getType());
        if ("REFUND".equals(type)) {
            return isCashPayment(transaction.getPaymentMethod());
        }
        return Set.of("EXPENSE", "WITHDRAWAL", "SUPPLIER_PAYMENT", "SUPPLIER_PAYMENT_OUT").contains(type);
    }

    private boolean isCashPayment(String paymentMethod) {
        String method = normalize(paymentMethod);
        return "CASH".equals(method) || "COUNTER_CASH".equals(method) || "CASH_REFUND".equals(method);
    }

    private BigDecimal netSales(AnalyticsData data) {
        return data.orders().stream()
                .map(order -> nvl(order.getTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(data.returns().stream()
                        .map(salesReturn -> nvl(salesReturn.getRefundAmount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal average(BigDecimal amount, long count) {
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return nvl(amount).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal growthPercent(BigDecimal current, BigDecimal previous) {
        BigDecimal currentValue = nvl(current);
        BigDecimal previousValue = nvl(previous);
        if (previousValue.compareTo(BigDecimal.ZERO) == 0) {
            return currentValue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        }
        return currentValue.subtract(previousValue)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousValue.abs(), 2, RoundingMode.HALF_UP);
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

    private long unreadCount(List<VarianceAlert> alerts) {
        return alerts.stream().filter(alert -> !alert.isRead()).count();
    }

    private String alertId(Long sessionId) {
        return "counter-variance-" + sessionId;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private Comparator<Counter> counterComparator() {
        return Comparator.comparing((Counter counter) -> safe(counter.getName()).toLowerCase(Locale.ROOT))
                .thenComparing(counter -> counter.getCounterId() == null ? Long.MAX_VALUE : counter.getCounterId());
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
        if (value == null) {
            return Optional.empty();
        }
        return buckets.stream()
                .filter(bucket -> !value.isBefore(bucket.from) && !value.isAfter(bucket.to))
                .findFirst();
    }

    private List<HourlyAccumulator> createHourlyBuckets(LocalDate date) {
        List<HourlyAccumulator> buckets = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            LocalDateTime bucketFrom = date.atTime(LocalTime.of(hour, 0));
            LocalDateTime bucketTo = hour == 23
                    ? date.plusDays(1).atStartOfDay().minusNanos(1)
                    : date.atTime(LocalTime.of(hour + 1, 0)).minusNanos(1);
            buckets.add(new HourlyAccumulator(
                    HOUR_LABEL.format(bucketFrom),
                    HOUR_LABEL.format(bucketFrom),
                    bucketFrom,
                    bucketTo));
        }
        return buckets;
    }

    private Optional<HourlyAccumulator> hourlyBucketFor(List<HourlyAccumulator> buckets, LocalDateTime value) {
        if (value == null) {
            return Optional.empty();
        }
        return buckets.stream()
                .filter(bucket -> !value.isBefore(bucket.from) && !value.isAfter(bucket.to))
                .findFirst();
    }

    private record Range(LocalDateTime from, LocalDateTime to) {
    }

    private record AnalyticsData(Branch branch,
                                 Range range,
                                 List<Counter> counters,
                                 Map<Long, Counter> countersById,
                                 List<CustomerOrder> orders,
                                 List<SalesReturn> returns,
                                 Map<Long, CustomerOrder> ordersById,
                                 List<CashSession> openedSessions,
                                 List<CashSession> closedSessions,
                                 List<CashSessionTransaction> transactions,
                                 Map<Long, CashSession> sessionsById) {
    }

    private class PerformanceAccumulator {
        private final String key;
        private final String label;
        private final LocalDateTime from;
        private final LocalDateTime to;
        private final int index;
        private BigDecimal revenue = BigDecimal.ZERO;
        private long transactions;

        private PerformanceAccumulator(String key, String label, LocalDateTime from, LocalDateTime to, int index) {
            this.key = key;
            this.label = label;
            this.from = from;
            this.to = to;
            this.index = index;
        }

        private void addRevenue(BigDecimal amount, long transactionCount) {
            revenue = revenue.add(nvl(amount));
            transactions += transactionCount;
        }

        private void subtractRevenue(BigDecimal amount) {
            revenue = revenue.subtract(nvl(amount));
        }

        private SalesPerformanceBucket toResponse() {
            return SalesPerformanceBucket.builder()
                    .bucketKey(key != null ? key : "bucket-" + index)
                    .label(label)
                    .bucketFrom(from)
                    .bucketTo(to)
                    .revenue(revenue)
                    .transactions(transactions)
                    .currency(CURRENCY)
                    .build();
        }
    }

    private class HourlyAccumulator {
        private final String hour;
        private final String label;
        private final LocalDateTime from;
        private final LocalDateTime to;
        private BigDecimal actualSales = BigDecimal.ZERO;
        private long transactions;

        private HourlyAccumulator(String hour, String label, LocalDateTime from, LocalDateTime to) {
            this.hour = hour;
            this.label = label;
            this.from = from;
            this.to = to;
        }

        private void addRevenue(BigDecimal amount, long transactionCount) {
            actualSales = actualSales.add(nvl(amount));
            transactions += transactionCount;
        }

        private void subtractRevenue(BigDecimal amount) {
            actualSales = actualSales.subtract(nvl(amount));
        }

        private BigDecimal actualSales() {
            return actualSales;
        }

        private HourlySalesTrend toResponse(BigDecimal maxSales) {
            return HourlySalesTrend.builder()
                    .hour(hour)
                    .label(label)
                    .bucketFrom(from)
                    .bucketTo(to)
                    .actualSales(actualSales)
                    .transactions(transactions)
                    .normalizedScore(percent(actualSales, maxSales))
                    .currency(CURRENCY)
                    .build();
        }
    }

    private class CounterAccumulator {
        private final Long counterId;
        private BigDecimal revenue = BigDecimal.ZERO;
        private long transactions;

        private CounterAccumulator(Long counterId) {
            this.counterId = counterId;
        }

        private void addRevenue(BigDecimal amount, long transactionCount) {
            revenue = revenue.add(nvl(amount));
            transactions += transactionCount;
        }

        private void subtractRevenue(BigDecimal amount) {
            revenue = revenue.subtract(nvl(amount));
        }

        private void addTransactions(long transactionCount) {
            transactions += transactionCount;
        }

        private BigDecimal revenue() {
            return revenue;
        }

        private long transactions() {
            return transactions;
        }

        private CounterPerformanceEntry toPerformanceResponse(int rank,
                                                              Counter counter,
                                                              BigDecimal maxRevenue,
                                                              long maxTransactions) {
            return CounterPerformanceEntry.builder()
                    .rank(rank)
                    .counterId(counterId)
                    .counterName(counter != null ? counter.getName() : "Unknown Counter")
                    .revenue(revenue)
                    .transactions(transactions)
                    .revenueBarPercent(percent(revenue, maxRevenue))
                    .transactionBarPercent(percent(
                            BigDecimal.valueOf(transactions),
                            BigDecimal.valueOf(maxTransactions)))
                    .currency(CURRENCY)
                    .build();
        }
    }
}
