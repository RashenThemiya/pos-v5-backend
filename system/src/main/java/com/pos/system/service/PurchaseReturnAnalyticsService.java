package com.pos.system.service;

import com.pos.system.dto.supplier.PurchaseReturnAnalyticsResponse;
import com.pos.system.dto.supplier.PurchaseReturnAnalyticsResponse.*;
import com.pos.system.model.auth.Branch;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.UnitMaster;
import com.pos.system.model.supplier.PurchaseReturn;
import com.pos.system.model.supplier.PurchaseReturnItem;
import com.pos.system.model.supplier.PurchaseReturnRefund;
import com.pos.system.model.supplier.Supplier;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.ItemRepository;
import com.pos.system.repository.ItemUnitRepository;
import com.pos.system.repository.PurchaseReturnItemRepository;
import com.pos.system.repository.PurchaseReturnRefundRepository;
import com.pos.system.repository.PurchaseReturnRepository;
import com.pos.system.repository.SupplierRepository;
import com.pos.system.repository.UnitMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseReturnAnalyticsService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final String DEFAULT_DATE_RANGE = "30D";
    private static final String CURRENCY = "Rs";
    private static final String DEFAULT_QTY_UNIT = "units";

    private final BranchRepository branchRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final PurchaseReturnItemRepository purchaseReturnItemRepository;
    private final PurchaseReturnRefundRepository purchaseReturnRefundRepository;
    private final SupplierRepository supplierRepository;
    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final UnitMasterRepository unitMasterRepository;

    public PurchaseReturnAnalyticsResponse getAnalytics(Long branchId,
                                                        LocalDateTime from,
                                                        LocalDateTime to,
                                                        String dateRange,
                                                        int supplierLimit,
                                                        int itemLimit) {
        Range range = resolveRange(from, to, dateRange);
        int safeSupplierLimit = sanitizeLimit(supplierLimit, 10);
        int safeItemLimit = sanitizeLimit(itemLimit, 10);
        AnalyticsData data = loadData(branchId, range);

        return PurchaseReturnAnalyticsResponse.builder()
                .context(buildContext(data.branch))
                .filters(AnalyticsFilters.builder()
                        .dateRange(resolveDateRangeLabel(dateRange, from, to))
                        .from(range.from())
                        .to(range.to())
                        .supplierLimit(safeSupplierLimit)
                        .itemLimit(safeItemLimit)
                        .build())
                .emptyState(buildEmptyState(data))
                .summary(buildSummary(data))
                .trend(buildTrend(data))
                .returnStatus(buildReturnStatus(data))
                .refundStatus(buildRefundStatus(data))
                .supplierAnalysis(buildSupplierAnalysis(data, safeSupplierLimit))
                .pendingAging(buildPendingAging(data))
                .mostReturnedItems(buildMostReturnedItems(data, safeItemLimit))
                .build();
    }

    public ReturnSummary getSummary(Long branchId,
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

    public List<ReturnStatusEntry> getReturnStatus(Long branchId,
                                                   LocalDateTime from,
                                                   LocalDateTime to,
                                                   String dateRange) {
        return buildReturnStatus(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public RefundStatus getRefundStatus(Long branchId,
                                        LocalDateTime from,
                                        LocalDateTime to,
                                        String dateRange) {
        return buildRefundStatus(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<SupplierReturnEntry> getSupplierAnalysis(Long branchId,
                                                         LocalDateTime from,
                                                         LocalDateTime to,
                                                         String dateRange,
                                                         int limit) {
        return buildSupplierAnalysis(loadData(branchId, resolveRange(from, to, dateRange)),
                sanitizeLimit(limit, 10));
    }

    public List<AgingBucket> getPendingAging(Long branchId,
                                             LocalDateTime from,
                                             LocalDateTime to,
                                             String dateRange) {
        return buildPendingAging(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<ReturnedItemEntry> getMostReturnedItems(Long branchId,
                                                        LocalDateTime from,
                                                        LocalDateTime to,
                                                        String dateRange,
                                                        int limit) {
        return buildMostReturnedItems(loadData(branchId, resolveRange(from, to, dateRange)),
                sanitizeLimit(limit, 10));
    }

    private AnalyticsData loadData(Long branchId, Range range) {
        Branch branch = requireBranch(branchId);
        List<PurchaseReturn> returns = purchaseReturnRepository.findByBranchIdOrderByReturnDateDesc(branchId).stream()
                .filter(purchaseReturn -> isWithin(purchaseReturn.getReturnDate(), range.from(), range.to()))
                .toList();
        List<Long> returnIds = returns.stream()
                .map(PurchaseReturn::getPurchaseReturnId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<PurchaseReturnItem>> itemsByReturnId = returnIds.isEmpty()
                ? Map.of()
                : purchaseReturnItemRepository.findByPurchaseReturnIdIn(returnIds).stream()
                .collect(Collectors.groupingBy(PurchaseReturnItem::getPurchaseReturnId));
        Map<Long, List<PurchaseReturnRefund>> refundsByReturnId = returnIds.isEmpty()
                ? Map.of()
                : purchaseReturnRefundRepository.findByPurchaseReturnIdIn(returnIds).stream()
                .collect(Collectors.groupingBy(PurchaseReturnRefund::getPurchaseReturnId));

        Map<Long, PurchaseReturnMetrics> metricsByReturnId = returns.stream()
                .collect(Collectors.toMap(
                        PurchaseReturn::getPurchaseReturnId,
                        purchaseReturn -> buildMetrics(
                                purchaseReturn,
                                itemsByReturnId.getOrDefault(purchaseReturn.getPurchaseReturnId(), List.of()),
                                refundsByReturnId.getOrDefault(purchaseReturn.getPurchaseReturnId(), List.of())),
                        (left, right) -> left));

        Set<Long> supplierIds = returns.stream()
                .map(PurchaseReturn::getSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Supplier> suppliersById = new HashMap<>();
        supplierRepository.findAllById(supplierIds)
                .forEach(supplier -> suppliersById.put(supplier.getSupplierId(), supplier));

        List<PurchaseReturnItem> allItems = itemsByReturnId.values().stream()
                .flatMap(List::stream)
                .toList();
        Set<Long> itemIds = allItems.stream()
                .map(PurchaseReturnItem::getItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Item> itemsById = new HashMap<>();
        itemRepository.findAllById(itemIds).forEach(item -> itemsById.put(item.getItemId(), item));

        Set<Long> unitIds = allItems.stream()
                .map(PurchaseReturnItem::getUnitId)
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
                returns,
                itemsByReturnId,
                refundsByReturnId,
                metricsByReturnId,
                suppliersById,
                itemsById,
                itemUnitsById,
                masterUnitsById);
    }

    private PageContext buildContext(Branch branch) {
        return PageContext.builder()
                .branchId(branch.getBranchId())
                .branchName(branch.getName())
                .title("Purchase Return Analytics")
                .subtitle("Supplier return trends, refund status, and return reasons")
                .backRoute("/purchase-returns")
                .build();
    }

    private EmptyState buildEmptyState(AnalyticsData data) {
        boolean empty = data.returns.isEmpty();
        return EmptyState.builder()
                .empty(empty)
                .message(empty ? "No purchase return data is available for the selected period." : null)
                .build();
    }

    private ReturnSummary buildSummary(AnalyticsData data) {
        List<PurchaseReturnMetrics> metrics = data.metricsByReturnId.values().stream().toList();
        List<PurchaseReturnMetrics> activeMetrics = activeMetrics(data);
        BigDecimal returnValue = activeMetrics.stream()
                .map(PurchaseReturnMetrics::returnValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingRefund = activeMetrics.stream()
                .map(PurchaseReturnMetrics::balanceDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundedAmount = activeMetrics.stream()
                .map(PurchaseReturnMetrics::refundedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnedQty = activeMetrics.stream()
                .map(PurchaseReturnMetrics::returnedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long completedOrRefunded = metrics.stream()
                .filter(metric -> !metric.cancelled())
                .filter(metric -> metric.completed() || metric.refunded())
                .count();

        return ReturnSummary.builder()
                .totalReturns(data.returns.size())
                .returnValue(returnValue)
                .pendingRefund(pendingRefund)
                .refundedAmount(refundedAmount)
                .returnedQty(returnedQty)
                .returnedQtyUnit(DEFAULT_QTY_UNIT)
                .refundCompRate(percent(BigDecimal.valueOf(completedOrRefunded), BigDecimal.valueOf(data.returns.size())))
                .currency(CURRENCY)
                .build();
    }

    private List<TrendBucket> buildTrend(AnalyticsData data) {
        List<TrendAccumulator> buckets = createBuckets(data.range.from(), data.range.to());
        for (PurchaseReturn purchaseReturn : data.returns) {
            PurchaseReturnMetrics metrics = data.metricsByReturnId.get(purchaseReturn.getPurchaseReturnId());
            if (metrics == null) {
                continue;
            }
            bucketFor(buckets, purchaseReturn.getReturnDate())
                    .ifPresent(bucket -> bucket.add(metrics));
        }
        return buckets.stream()
                .map(TrendAccumulator::toResponse)
                .toList();
    }

    private List<ReturnStatusEntry> buildReturnStatus(AnalyticsData data) {
        long cancelled = data.metricsByReturnId.values().stream()
                .filter(PurchaseReturnMetrics::cancelled)
                .count();
        long completed = data.metricsByReturnId.values().stream()
                .filter(metric -> !metric.cancelled())
                .filter(PurchaseReturnMetrics::completed)
                .count();
        long pending = data.returns.size() - completed - cancelled;

        return List.of(
                ReturnStatusEntry.builder().status("PENDING").count(pending).build(),
                ReturnStatusEntry.builder().status("COMPLETED").count(completed).build(),
                ReturnStatusEntry.builder().status("CANCELLED").count(cancelled).build());
    }

    private RefundStatus buildRefundStatus(AnalyticsData data) {
        List<PurchaseReturnMetrics> activeMetrics = activeMetrics(data);
        BigDecimal pending = activeMetrics.stream()
                .filter(metric -> "UNPAID".equalsIgnoreCase(metric.paymentStatus())
                        || "PENDING".equalsIgnoreCase(metric.paymentStatus()))
                .map(PurchaseReturnMetrics::balanceDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = activeMetrics.stream()
                .map(PurchaseReturnMetrics::refundedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = activeMetrics.stream()
                .filter(metric -> "PARTIAL".equalsIgnoreCase(metric.paymentStatus())
                        || "PARTIALLY_REFUNDED".equalsIgnoreCase(metric.paymentStatus()))
                .map(PurchaseReturnMetrics::balanceDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RefundStatusEntry> rows = List.of(
                RefundStatusEntry.builder().status("PENDING").amount(pending).build(),
                RefundStatusEntry.builder().status("REFUNDED").amount(refunded).build(),
                RefundStatusEntry.builder().status("REMAINING").amount(remaining).build());

        return RefundStatus.builder()
                .pending(pending)
                .refunded(refunded)
                .remaining(remaining)
                .currency(CURRENCY)
                .rows(rows)
                .build();
    }

    private List<SupplierReturnEntry> buildSupplierAnalysis(AnalyticsData data, int limit) {
        Map<Long, SupplierAccumulator> suppliers = new HashMap<>();
        for (PurchaseReturn purchaseReturn : data.returns) {
            PurchaseReturnMetrics metrics = data.metricsByReturnId.get(purchaseReturn.getPurchaseReturnId());
            if (metrics == null || metrics.cancelled()) {
                continue;
            }
            suppliers.computeIfAbsent(purchaseReturn.getSupplierId(), SupplierAccumulator::new)
                    .add(metrics);
        }

        long maxCount = suppliers.values().stream()
                .mapToLong(SupplierAccumulator::returnCount)
                .max()
                .orElse(0);

        return suppliers.values().stream()
                .sorted(Comparator.comparingLong(SupplierAccumulator::returnCount).reversed()
                        .thenComparing(accumulator -> supplierName(data, accumulator.supplierId),
                                String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(accumulator -> accumulator.toResponse(data.suppliersById.get(accumulator.supplierId), maxCount))
                .toList();
    }

    private List<AgingBucket> buildPendingAging(AnalyticsData data) {
        List<AgingAccumulator> buckets = agingBuckets();
        LocalDateTime now = LocalDateTime.now();
        for (PurchaseReturn purchaseReturn : data.returns) {
            PurchaseReturnMetrics metrics = data.metricsByReturnId.get(purchaseReturn.getPurchaseReturnId());
            if (metrics == null || metrics.cancelled() || metrics.balanceDue().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            long ageDays = Math.max(0, Duration.between(purchaseReturn.getReturnDate(), now).toDays());
            buckets.stream()
                    .filter(bucket -> bucket.matches(ageDays))
                    .findFirst()
                    .ifPresent(AgingAccumulator::increment);
        }

        return buckets.stream()
                .map(AgingAccumulator::toResponse)
                .toList();
    }

    private List<ReturnedItemEntry> buildMostReturnedItems(AnalyticsData data, int limit) {
        Map<Long, ItemAccumulator> itemAccumulators = new HashMap<>();
        for (PurchaseReturn purchaseReturn : data.returns) {
            PurchaseReturnMetrics metrics = data.metricsByReturnId.get(purchaseReturn.getPurchaseReturnId());
            if (metrics == null || metrics.cancelled()) {
                continue;
            }
            for (PurchaseReturnItem item : data.itemsByReturnId.getOrDefault(purchaseReturn.getPurchaseReturnId(), List.of())) {
                itemAccumulators.computeIfAbsent(item.getItemId(), ItemAccumulator::new)
                        .add(item, safe(purchaseReturn.getReason()), unitName(data, item.getUnitId()));
            }
        }

        List<ItemAccumulator> ranked = itemAccumulators.values().stream()
                .sorted(Comparator.comparing(ItemAccumulator::returnedQty).reversed()
                        .thenComparing(ItemAccumulator::returnValue, Comparator.reverseOrder()))
                .limit(limit)
                .toList();

        List<ReturnedItemEntry> rows = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            rows.add(ranked.get(index).toResponse(index + 1, data.itemsById.get(ranked.get(index).itemId)));
        }
        return rows;
    }

    private PurchaseReturnMetrics buildMetrics(PurchaseReturn purchaseReturn,
                                               List<PurchaseReturnItem> items,
                                               List<PurchaseReturnRefund> refunds) {
        BigDecimal itemValue = items.stream()
                .map(item -> nvl(item.getLineTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnValue = itemValue.compareTo(BigDecimal.ZERO) > 0
                ? itemValue
                : nvl(purchaseReturn.getRefundAmount());
        BigDecimal returnedQty = items.stream()
                .map(item -> nvl(item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundRecordTotal = refunds.stream()
                .map(refund -> nvl(refund.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = nvl(purchaseReturn.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0
                ? nvl(purchaseReturn.getPaidAmount())
                : refundRecordTotal;
        BigDecimal refundDue = nvl(purchaseReturn.getRefundAmount()).compareTo(BigDecimal.ZERO) > 0
                ? nvl(purchaseReturn.getRefundAmount())
                : returnValue;
        BigDecimal balanceDue = purchaseReturn.getBalanceDue() != null
                ? nvl(purchaseReturn.getBalanceDue())
                : refundDue.subtract(paidAmount).max(BigDecimal.ZERO);
        String paymentStatus = normalizeStatus(purchaseReturn.getPaymentStatus(), "UNPAID");
        boolean cancelled = isCancelled(purchaseReturn.getStatus());
        boolean completed = "COMPLETED".equalsIgnoreCase(safe(purchaseReturn.getStatus()));
        boolean refunded = "REFUNDED".equalsIgnoreCase(paymentStatus)
                || balanceDue.compareTo(BigDecimal.ZERO) <= 0 && paidAmount.compareTo(BigDecimal.ZERO) > 0;

        return new PurchaseReturnMetrics(
                purchaseReturn.getPurchaseReturnId(),
                returnValue,
                refundDue,
                paidAmount,
                balanceDue.max(BigDecimal.ZERO),
                returnedQty,
                normalizeStatus(purchaseReturn.getStatus(), "PENDING"),
                paymentStatus,
                cancelled,
                completed,
                refunded);
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

    private List<PurchaseReturnMetrics> activeMetrics(AnalyticsData data) {
        return data.metricsByReturnId.values().stream()
                .filter(metric -> !metric.cancelled())
                .toList();
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

    private List<AgingAccumulator> agingBuckets() {
        return List.of(
                new AgingAccumulator("0_7", "0-7 Days", 0, 7),
                new AgingAccumulator("8_14", "8-14 Days", 8, 14),
                new AgingAccumulator("15_30", "15-30 Days", 15, 30),
                new AgingAccumulator("30_PLUS", "30+ Days", 31, null));
    }

    private String supplierName(AnalyticsData data, Long supplierId) {
        Supplier supplier = data.suppliersById.get(supplierId);
        return supplier != null ? supplier.getName() : "Unknown Supplier";
    }

    private String unitName(AnalyticsData data, Long unitId) {
        ItemUnit unit = data.itemUnitsById.get(unitId);
        if (unit == null) {
            return null;
        }
        UnitMaster masterUnit = unit.getMasterUnitId() != null
                ? data.masterUnitsById.get(unit.getMasterUnitId())
                : null;
        return masterUnit != null ? masterUnit.getName() : unit.getUnitName();
    }

    private String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        return status.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private boolean isCancelled(String status) {
        String normalized = normalizeStatus(status, "");
        return "CANCELLED".equals(normalized)
                || "CANCELED".equals(normalized)
                || "VOID".equals(normalized)
                || "VOIDED".equals(normalized);
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

    private record AnalyticsData(Branch branch,
                                 Range range,
                                 List<PurchaseReturn> returns,
                                 Map<Long, List<PurchaseReturnItem>> itemsByReturnId,
                                 Map<Long, List<PurchaseReturnRefund>> refundsByReturnId,
                                 Map<Long, PurchaseReturnMetrics> metricsByReturnId,
                                 Map<Long, Supplier> suppliersById,
                                 Map<Long, Item> itemsById,
                                 Map<Long, ItemUnit> itemUnitsById,
                                 Map<Long, UnitMaster> masterUnitsById) {
    }

    private record PurchaseReturnMetrics(Long purchaseReturnId,
                                         BigDecimal returnValue,
                                         BigDecimal refundDue,
                                         BigDecimal refundedAmount,
                                         BigDecimal balanceDue,
                                         BigDecimal returnedQty,
                                         String status,
                                         String paymentStatus,
                                         boolean cancelled,
                                         boolean completed,
                                         boolean refunded) {
    }

    private static class TrendAccumulator {
        private final String key;
        private final String label;
        private final LocalDateTime from;
        private final LocalDateTime to;
        private final int index;
        private BigDecimal returnValue = BigDecimal.ZERO;
        private long returnCount;

        private TrendAccumulator(String key, String label, LocalDateTime from, LocalDateTime to, int index) {
            this.key = key;
            this.label = label;
            this.from = from;
            this.to = to;
            this.index = index;
        }

        private void add(PurchaseReturnMetrics metrics) {
            returnCount++;
            if (!metrics.cancelled()) {
                returnValue = returnValue.add(metrics.returnValue());
            }
        }

        private TrendBucket toResponse() {
            return TrendBucket.builder()
                    .bucketKey(key != null ? key : "bucket-" + index)
                    .label(label)
                    .bucketFrom(from)
                    .bucketTo(to)
                    .returnValue(returnValue)
                    .returnCount(returnCount)
                    .build();
        }
    }

    private class SupplierAccumulator {
        private final Long supplierId;
        private final Set<Long> returnIds = new HashSet<>();
        private BigDecimal returnedQty = BigDecimal.ZERO;
        private BigDecimal returnValue = BigDecimal.ZERO;

        private SupplierAccumulator(Long supplierId) {
            this.supplierId = supplierId;
        }

        private void add(PurchaseReturnMetrics metrics) {
            if (returnIds.add(metrics.purchaseReturnId())) {
                returnedQty = returnedQty.add(metrics.returnedQty());
                returnValue = returnValue.add(metrics.returnValue());
            }
        }

        private long returnCount() {
            return returnIds.size();
        }

        private SupplierReturnEntry toResponse(Supplier supplier, long maxCount) {
            return SupplierReturnEntry.builder()
                    .supplierId(supplierId)
                    .supplierName(supplier != null ? supplier.getName() : "Unknown Supplier")
                    .returnCount(returnCount())
                    .returnedQty(returnedQty)
                    .returnValue(returnValue)
                    .barPercent(percent(BigDecimal.valueOf(returnCount()), BigDecimal.valueOf(maxCount)))
                    .metric("RETURN_COUNT")
                    .build();
        }
    }

    private static class AgingAccumulator {
        private final String key;
        private final String label;
        private final int minDays;
        private final Integer maxDays;
        private long count;

        private AgingAccumulator(String key, String label, int minDays, Integer maxDays) {
            this.key = key;
            this.label = label;
            this.minDays = minDays;
            this.maxDays = maxDays;
        }

        private boolean matches(long ageDays) {
            return ageDays >= minDays && (maxDays == null || ageDays <= maxDays);
        }

        private void increment() {
            count++;
        }

        private AgingBucket toResponse() {
            return AgingBucket.builder()
                    .bucketKey(key)
                    .label(label)
                    .minDays(minDays)
                    .maxDays(maxDays)
                    .count(count)
                    .build();
        }
    }

    private static class ItemAccumulator {
        private final Long itemId;
        private BigDecimal returnedQty = BigDecimal.ZERO;
        private BigDecimal returnValue = BigDecimal.ZERO;
        private final Map<String, Long> reasonCounts = new LinkedHashMap<>();
        private final Map<String, Long> unitCounts = new LinkedHashMap<>();

        private ItemAccumulator(Long itemId) {
            this.itemId = itemId;
        }

        private void add(PurchaseReturnItem item, String reason, String unitName) {
            returnedQty = returnedQty.add(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
            returnValue = returnValue.add(item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO);
            if (reason != null && !reason.isBlank()) {
                reasonCounts.merge(reason, 1L, Long::sum);
            }
            if (unitName != null && !unitName.isBlank()) {
                unitCounts.merge(unitName, 1L, Long::sum);
            }
        }

        private BigDecimal returnedQty() {
            return returnedQty;
        }

        private BigDecimal returnValue() {
            return returnValue;
        }

        private ReturnedItemEntry toResponse(int rank, Item item) {
            return ReturnedItemEntry.builder()
                    .rank(rank)
                    .productId(itemId)
                    .productName(item != null ? item.getName() : "Unknown Product")
                    .sku(item != null ? item.getSku() : null)
                    .returnedQty(returnedQty)
                    .unitName(mostFrequent(unitCounts))
                    .returnValue(returnValue)
                    .reason(mostFrequent(reasonCounts))
                    .build();
        }

        private static String mostFrequent(Map<String, Long> counts) {
            return counts.entrySet().stream()
                    .max(Map.Entry.<String, Long>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }
    }
}
