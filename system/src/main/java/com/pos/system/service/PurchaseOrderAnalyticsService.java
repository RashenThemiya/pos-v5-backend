package com.pos.system.service;

import com.pos.system.dto.supplier.PurchaseOrderAnalyticsResponse;
import com.pos.system.dto.supplier.PurchaseOrderAnalyticsResponse.*;
import com.pos.system.model.auth.Branch;
import com.pos.system.model.supplier.PurchaseOrder;
import com.pos.system.model.supplier.PurchaseOrderItem;
import com.pos.system.model.supplier.SupplierPayment;
import com.pos.system.model.supplier.Supply;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.PurchaseOrderItemRepository;
import com.pos.system.repository.PurchaseOrderRepository;
import com.pos.system.repository.SupplierPaymentRepository;
import com.pos.system.repository.SupplyRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderAnalyticsService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final String DEFAULT_DATE_RANGE = "30D";
    private static final String CURRENCY = "Rs";

    private final BranchRepository branchRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplyRepository supplyRepository;

    public PurchaseOrderAnalyticsResponse getAnalytics(Long branchId,
                                                       LocalDateTime from,
                                                       LocalDateTime to,
                                                       String dateRange) {
        Range range = resolveRange(from, to, dateRange);
        AnalyticsData data = loadData(branchId, range);

        return PurchaseOrderAnalyticsResponse.builder()
                .context(buildContext(data.branch))
                .filters(AnalyticsFilters.builder()
                        .dateRange(resolveDateRangeLabel(dateRange, from, to))
                        .from(range.from())
                        .to(range.to())
                        .build())
                .emptyState(buildEmptyState(data))
                .summary(buildSummary(data))
                .spendTrend(buildSpendTrend(data))
                .status(buildStatus(data))
                .receivingPerformance(buildReceivingPerformance(data))
                .valueDistribution(buildValueDistribution(data))
                .paymentExposure(buildPaymentExposure(data))
                .build();
    }

    public Summary getSummary(Long branchId,
                              LocalDateTime from,
                              LocalDateTime to,
                              String dateRange) {
        return buildSummary(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<SpendBucket> getSpendTrend(Long branchId,
                                           LocalDateTime from,
                                           LocalDateTime to,
                                           String dateRange) {
        return buildSpendTrend(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public StatusBreakdown getStatus(Long branchId,
                                     LocalDateTime from,
                                     LocalDateTime to,
                                     String dateRange) {
        return buildStatus(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public ReceivingPerformance getReceivingPerformance(Long branchId,
                                                        LocalDateTime from,
                                                        LocalDateTime to,
                                                        String dateRange) {
        return buildReceivingPerformance(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<ValueDistribution> getValueDistribution(Long branchId,
                                                        LocalDateTime from,
                                                        LocalDateTime to,
                                                        String dateRange) {
        return buildValueDistribution(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public PaymentExposure getPaymentExposure(Long branchId,
                                              LocalDateTime from,
                                              LocalDateTime to,
                                              String dateRange) {
        return buildPaymentExposure(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    private AnalyticsData loadData(Long branchId, Range range) {
        Branch branch = requireBranch(branchId);
        List<PurchaseOrder> purchaseOrders = purchaseOrderRepository.findByBranchId(branchId).stream()
                .filter(po -> isWithin(po.getCreatedAt(), range.from(), range.to()))
                .toList();
        List<Long> poIds = purchaseOrders.stream()
                .map(PurchaseOrder::getPoId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<PurchaseOrderItem>> itemsByPoId = poIds.isEmpty()
                ? Map.of()
                : purchaseOrderItemRepository.findByPoIdIn(poIds).stream()
                .collect(Collectors.groupingBy(PurchaseOrderItem::getPoId));
        Map<Long, List<SupplierPayment>> paymentsByPoId = poIds.isEmpty()
                ? Map.of()
                : supplierPaymentRepository.findByPoIdIn(poIds).stream()
                .collect(Collectors.groupingBy(SupplierPayment::getPoId));
        Map<Long, List<Supply>> suppliesByPoId = poIds.isEmpty()
                ? Map.of()
                : supplyRepository.findByPoIdIn(poIds).stream()
                .filter(supply -> supply.getPoId() != null)
                .collect(Collectors.groupingBy(Supply::getPoId));

        Map<Long, PurchaseOrderMetrics> metricsByPoId = purchaseOrders.stream()
                .collect(Collectors.toMap(
                        PurchaseOrder::getPoId,
                        po -> buildMetrics(
                                po,
                                itemsByPoId.getOrDefault(po.getPoId(), List.of()),
                                paymentsByPoId.getOrDefault(po.getPoId(), List.of()),
                                suppliesByPoId.getOrDefault(po.getPoId(), List.of())),
                        (left, right) -> left));

        return new AnalyticsData(branch, range, purchaseOrders, metricsByPoId);
    }

    private PageContext buildContext(Branch branch) {
        return PageContext.builder()
                .branchId(branch.getBranchId())
                .branchName(branch.getName())
                .title("Purchase Order Analytics")
                .description("Procurement performance, receiving progress and supplier risk.")
                .build();
    }

    private EmptyState buildEmptyState(AnalyticsData data) {
        boolean empty = data.purchaseOrders.isEmpty();
        return EmptyState.builder()
                .empty(empty)
                .message(empty ? "No purchase order data is available for the selected period." : null)
                .build();
    }

    private Summary buildSummary(AnalyticsData data) {
        List<PurchaseOrderMetrics> activeMetrics = activeMetrics(data);
        BigDecimal spend = activeMetrics.stream()
                .map(PurchaseOrderMetrics::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long open = activeMetrics.stream()
                .filter(PurchaseOrderMetrics::open)
                .count();
        long overdue = activeMetrics.stream()
                .filter(PurchaseOrderMetrics::overdue)
                .count();
        BigDecimal balance = activeMetrics.stream()
                .filter(metric -> metric.open()
                        || "PARTIALLY_RECEIVED".equalsIgnoreCase(metric.receivingStatus()))
                .map(PurchaseOrderMetrics::balanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Summary.builder()
                .totalPurchaseOrders(data.purchaseOrders.size())
                .poSpend(spend)
                .openPurchaseOrders(open)
                .overduePurchaseOrders(overdue)
                .balance(balance)
                .currency(CURRENCY)
                .build();
    }

    private List<SpendBucket> buildSpendTrend(AnalyticsData data) {
        List<SpendAccumulator> buckets = createBuckets(data.range.from(), data.range.to());
        Map<Long, PurchaseOrderMetrics> metricsByPoId = data.metricsByPoId;

        for (PurchaseOrder purchaseOrder : data.purchaseOrders) {
            PurchaseOrderMetrics metrics = metricsByPoId.get(purchaseOrder.getPoId());
            if (metrics == null || metrics.cancelled()) {
                continue;
            }

            bucketFor(buckets, purchaseOrder.getCreatedAt())
                    .ifPresent(bucket -> bucket.add(metrics));
        }

        return buckets.stream()
                .map(SpendAccumulator::toResponse)
                .toList();
    }

    private StatusBreakdown buildStatus(AnalyticsData data) {
        long cancelled = data.metricsByPoId.values().stream()
                .filter(PurchaseOrderMetrics::cancelled)
                .count();
        long partiallyReceived = data.metricsByPoId.values().stream()
                .filter(metric -> !metric.cancelled())
                .filter(metric -> "PARTIALLY_RECEIVED".equalsIgnoreCase(metric.receivingStatus()))
                .count();
        long completed = data.metricsByPoId.values().stream()
                .filter(metric -> !metric.cancelled())
                .filter(PurchaseOrderMetrics::completed)
                .count();
        long open = data.metricsByPoId.values().stream()
                .filter(metric -> !metric.cancelled())
                .filter(PurchaseOrderMetrics::open)
                .count();

        return StatusBreakdown.builder()
                .totalPurchaseOrders(data.purchaseOrders.size())
                .open(open)
                .completed(completed)
                .cancelled(cancelled)
                .partiallyReceived(partiallyReceived)
                .build();
    }

    private ReceivingPerformance buildReceivingPerformance(AnalyticsData data) {
        List<PurchaseOrderMetrics> activeMetrics = activeMetrics(data);
        BigDecimal ordered = activeMetrics.stream()
                .map(PurchaseOrderMetrics::orderedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = activeMetrics.stream()
                .map(PurchaseOrderMetrics::receivedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = ordered.subtract(received).max(BigDecimal.ZERO);

        return ReceivingPerformance.builder()
                .orderedQuantity(ordered)
                .receivedQuantity(received)
                .remainingQuantity(remaining)
                .completionPercent(percent(received, ordered))
                .build();
    }

    private List<ValueDistribution> buildValueDistribution(AnalyticsData data) {
        List<PurchaseOrderMetrics> activeMetrics = activeMetrics(data);
        List<ValueBucket> buckets = valueBuckets();

        for (PurchaseOrderMetrics metrics : activeMetrics) {
            buckets.stream()
                    .filter(bucket -> bucket.matches(metrics.totalAmount()))
                    .findFirst()
                    .ifPresent(ValueBucket::increment);
        }

        BigDecimal total = BigDecimal.valueOf(activeMetrics.size());
        return buckets.stream()
                .map(bucket -> bucket.toResponse(total))
                .toList();
    }

    private PaymentExposure buildPaymentExposure(AnalyticsData data) {
        List<PurchaseOrderMetrics> activeMetrics = activeMetrics(data);
        BigDecimal paid = activeMetrics.stream()
                .filter(metric -> "PAID".equalsIgnoreCase(metric.paymentStatus()))
                .map(PurchaseOrderMetrics::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal partiallyPaid = activeMetrics.stream()
                .filter(metric -> "PARTIAL".equalsIgnoreCase(metric.paymentStatus()))
                .map(PurchaseOrderMetrics::paidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = activeMetrics.stream()
                .map(PurchaseOrderMetrics::balanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long paidCount = activeMetrics.stream()
                .filter(metric -> "PAID".equalsIgnoreCase(metric.paymentStatus()))
                .count();
        long partialCount = activeMetrics.stream()
                .filter(metric -> "PARTIAL".equalsIgnoreCase(metric.paymentStatus()))
                .count();
        long outstandingCount = activeMetrics.stream()
                .filter(metric -> metric.balanceAmount().compareTo(BigDecimal.ZERO) > 0)
                .count();

        List<PaymentExposureRow> rows = List.of(
                PaymentExposureRow.builder()
                        .key("PAID")
                        .label("Paid")
                        .amount(paid)
                        .purchaseOrderCount(paidCount)
                        .build(),
                PaymentExposureRow.builder()
                        .key("PARTIALLY_PAID")
                        .label("Partially Paid")
                        .amount(partiallyPaid)
                        .purchaseOrderCount(partialCount)
                        .build(),
                PaymentExposureRow.builder()
                        .key("OUTSTANDING")
                        .label("Outstanding")
                        .amount(outstanding)
                        .purchaseOrderCount(outstandingCount)
                        .build());

        return PaymentExposure.builder()
                .paid(paid)
                .partiallyPaid(partiallyPaid)
                .outstanding(outstanding)
                .currency(CURRENCY)
                .rows(rows)
                .build();
    }

    private PurchaseOrderMetrics buildMetrics(PurchaseOrder purchaseOrder,
                                              List<PurchaseOrderItem> items,
                                              List<SupplierPayment> payments,
                                              List<Supply> supplies) {
        BigDecimal totalAmount = items.stream()
                .map(item -> nvl(item.getOrderedQty()).multiply(nvl(item.getUnitCostEst())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ordered = items.stream()
                .map(item -> nvl(item.getOrderedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = items.stream()
                .map(item -> nvl(item.getReceivedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = ordered.subtract(received).max(BigDecimal.ZERO);
        BigDecimal paid = calculatePaidAmount(payments, supplies);
        BigDecimal balance = totalAmount.subtract(paid).max(BigDecimal.ZERO);
        String receivingStatus = resolveReceivingStatus(items, purchaseOrder.getStatus());
        String paymentStatus = resolvePaymentStatus(totalAmount, paid);
        boolean cancelled = isCancelledPurchaseOrder(purchaseOrder);
        boolean completed = isCompletedPurchaseOrder(purchaseOrder) || "FULLY_RECEIVED".equals(receivingStatus);
        boolean open = !cancelled && !completed;
        boolean overdue = open
                && purchaseOrder.getExpectedDate() != null
                && purchaseOrder.getExpectedDate().isBefore(LocalDate.now());

        return new PurchaseOrderMetrics(
                purchaseOrder.getPoId(),
                totalAmount,
                paid,
                balance,
                ordered,
                received,
                remaining,
                receivingStatus,
                paymentStatus,
                cancelled,
                completed,
                open,
                overdue);
    }

    private BigDecimal calculatePaidAmount(List<SupplierPayment> payments, List<Supply> supplies) {
        BigDecimal directPoPayments = payments.stream()
                .filter(payment -> payment.getSupplyId() == null)
                .map(payment -> nvl(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal supplyPayments = supplies.stream()
                .filter(this::isSupplyActive)
                .map(supply -> nvl(supply.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return directPoPayments.add(supplyPayments);
    }

    private String resolveReceivingStatus(List<PurchaseOrderItem> items, String orderStatus) {
        if ("CANCELLED".equalsIgnoreCase(safe(orderStatus)) || "CANCELED".equalsIgnoreCase(safe(orderStatus))) {
            return "CANCELLED";
        }
        if (items == null || items.isEmpty()) {
            return "NOT_RECEIVED";
        }

        BigDecimal receivedTotal = items.stream()
                .map(item -> nvl(item.getReceivedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (receivedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return "NOT_RECEIVED";
        }

        boolean fullyReceived = items.stream()
                .allMatch(item -> nvl(item.getReceivedQty()).compareTo(nvl(item.getOrderedQty())) >= 0);

        return fullyReceived ? "FULLY_RECEIVED" : "PARTIALLY_RECEIVED";
    }

    private String resolvePaymentStatus(BigDecimal total, BigDecimal paid) {
        total = nvl(total);
        paid = nvl(paid);

        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return "UNPAID";
        }
        if (paid.compareTo(total) >= 0) {
            return "PAID";
        }
        return "PARTIAL";
    }

    private List<PurchaseOrderMetrics> activeMetrics(AnalyticsData data) {
        return data.metricsByPoId.values().stream()
                .filter(metric -> !metric.cancelled())
                .toList();
    }

    private List<SpendAccumulator> createBuckets(LocalDateTime from, LocalDateTime to) {
        long dayCount = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()) + 1;
        if (dayCount <= 31) {
            return createDailyBuckets(from, to);
        }
        if (dayCount <= 92) {
            return createWeeklyBuckets(from, to);
        }
        return createMonthlyBuckets(from, to);
    }

    private List<SpendAccumulator> createDailyBuckets(LocalDateTime from, LocalDateTime to) {
        List<SpendAccumulator> buckets = new ArrayList<>();
        int index = 1;
        for (LocalDate date = from.toLocalDate(); !date.isAfter(to.toLocalDate()); date = date.plusDays(1)) {
            LocalDateTime bucketFrom = date.atStartOfDay().isBefore(from) ? from : date.atStartOfDay();
            LocalDateTime bucketTo = date.plusDays(1).atStartOfDay().minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new SpendAccumulator(date.toString(), DAY_LABEL.format(date), bucketFrom, bucketTo, index++));
        }
        return buckets;
    }

    private List<SpendAccumulator> createWeeklyBuckets(LocalDateTime from, LocalDateTime to) {
        List<SpendAccumulator> buckets = new ArrayList<>();
        LocalDateTime cursor = from;
        int index = 1;
        while (!cursor.isAfter(to)) {
            LocalDateTime bucketTo = cursor.plusDays(7).minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new SpendAccumulator("week-" + index, "Week " + index, cursor, bucketTo, index));
            cursor = bucketTo.plusNanos(1);
            index++;
        }
        return buckets;
    }

    private List<SpendAccumulator> createMonthlyBuckets(LocalDateTime from, LocalDateTime to) {
        List<SpendAccumulator> buckets = new ArrayList<>();
        LocalDateTime cursor = from;
        int index = 1;
        while (!cursor.isAfter(to)) {
            LocalDateTime nextMonth = cursor.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay();
            LocalDateTime bucketTo = nextMonth.minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new SpendAccumulator(
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

    private Optional<SpendAccumulator> bucketFor(List<SpendAccumulator> buckets, LocalDateTime value) {
        return buckets.stream()
                .filter(bucket -> isWithin(value, bucket.from, bucket.to))
                .findFirst();
    }

    private List<ValueBucket> valueBuckets() {
        return List.of(
                new ValueBucket("0_50K", "Rs. 0-50K", BigDecimal.ZERO, BigDecimal.valueOf(50000), true),
                new ValueBucket("50K_100K", "Rs. 50K-100K", BigDecimal.valueOf(50000), BigDecimal.valueOf(100000), true),
                new ValueBucket("100K_250K", "Rs. 100K-250K", BigDecimal.valueOf(100000), BigDecimal.valueOf(250000), true),
                new ValueBucket("250K_PLUS", "Rs. 250K+", BigDecimal.valueOf(250000), null, false));
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

    private boolean isCompletedPurchaseOrder(PurchaseOrder purchaseOrder) {
        String status = safe(purchaseOrder.getStatus()).toUpperCase(Locale.ROOT);
        return "COMPLETED".equals(status)
                || "FULLY_RECEIVED".equals(status)
                || "CLOSED".equals(status);
    }

    private boolean isCancelledPurchaseOrder(PurchaseOrder purchaseOrder) {
        String status = safe(purchaseOrder.getStatus()).toUpperCase(Locale.ROOT);
        return "CANCELLED".equals(status)
                || "CANCELED".equals(status)
                || "VOID".equals(status)
                || "VOIDED".equals(status);
    }

    private boolean isSupplyActive(Supply supply) {
        String status = safe(supply.getStatus()).toUpperCase(Locale.ROOT);
        return !"CANCELLED".equals(status)
                && !"CANCELED".equals(status)
                && !"VOID".equals(status)
                && !"VOIDED".equals(status);
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record Range(LocalDateTime from, LocalDateTime to) {
    }

    private record AnalyticsData(Branch branch,
                                 Range range,
                                 List<PurchaseOrder> purchaseOrders,
                                 Map<Long, PurchaseOrderMetrics> metricsByPoId) {
    }

    private record PurchaseOrderMetrics(Long poId,
                                        BigDecimal totalAmount,
                                        BigDecimal paidAmount,
                                        BigDecimal balanceAmount,
                                        BigDecimal orderedQuantity,
                                        BigDecimal receivedQuantity,
                                        BigDecimal remainingQuantity,
                                        String receivingStatus,
                                        String paymentStatus,
                                        boolean cancelled,
                                        boolean completed,
                                        boolean open,
                                        boolean overdue) {
    }

    private static class SpendAccumulator {
        private final String key;
        private final String label;
        private final LocalDateTime from;
        private final LocalDateTime to;
        private final int index;
        private final Set<Long> poIds = new HashSet<>();
        private BigDecimal poValue = BigDecimal.ZERO;

        private SpendAccumulator(String key, String label, LocalDateTime from, LocalDateTime to, int index) {
            this.key = key;
            this.label = label;
            this.from = from;
            this.to = to;
            this.index = index;
        }

        private void add(PurchaseOrderMetrics metrics) {
            if (poIds.add(metrics.poId())) {
                poValue = poValue.add(metrics.totalAmount());
            }
        }

        private SpendBucket toResponse() {
            return SpendBucket.builder()
                    .bucketKey(key != null ? key : "bucket-" + index)
                    .label(label)
                    .bucketFrom(from)
                    .bucketTo(to)
                    .purchaseOrderCount(poIds.size())
                    .poValue(poValue)
                    .build();
        }
    }

    private class ValueBucket {
        private final String key;
        private final String label;
        private final BigDecimal minValue;
        private final BigDecimal maxValue;
        private final boolean upperExclusive;
        private long count;

        private ValueBucket(String key,
                            String label,
                            BigDecimal minValue,
                            BigDecimal maxValue,
                            boolean upperExclusive) {
            this.key = key;
            this.label = label;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.upperExclusive = upperExclusive;
        }

        private boolean matches(BigDecimal value) {
            BigDecimal amount = nvl(value);
            if (amount.compareTo(minValue) < 0) {
                return false;
            }
            if (maxValue == null) {
                return true;
            }
            return upperExclusive
                    ? amount.compareTo(maxValue) < 0
                    : amount.compareTo(maxValue) <= 0;
        }

        private void increment() {
            count++;
        }

        private ValueDistribution toResponse(BigDecimal total) {
            return ValueDistribution.builder()
                    .bracketKey(key)
                    .label(label)
                    .minValue(minValue)
                    .maxValue(maxValue)
                    .purchaseOrderCount(count)
                    .percentage(percent(BigDecimal.valueOf(count), total))
                    .build();
        }
    }
}
