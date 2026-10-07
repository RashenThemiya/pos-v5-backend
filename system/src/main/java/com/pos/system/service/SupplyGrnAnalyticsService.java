package com.pos.system.service;

import com.pos.system.dto.supplier.SupplyGrnAnalyticsResponse;
import com.pos.system.dto.supplier.SupplyGrnAnalyticsResponse.*;
import com.pos.system.model.auth.Branch;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.UnitMaster;
import com.pos.system.model.supplier.PurchaseOrderItem;
import com.pos.system.model.supplier.Supplier;
import com.pos.system.model.supplier.Supply;
import com.pos.system.model.supplier.SupplyProduct;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.ItemRepository;
import com.pos.system.repository.ItemUnitRepository;
import com.pos.system.repository.PurchaseOrderItemRepository;
import com.pos.system.repository.SupplierRepository;
import com.pos.system.repository.SupplyProductRepository;
import com.pos.system.repository.SupplyRepository;
import com.pos.system.repository.UnitMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class SupplyGrnAnalyticsService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final String DEFAULT_DATE_RANGE = "30D";
    private static final String CURRENCY = "Rs";
    private static final String DEFAULT_QTY_UNIT = "base units";

    private final BranchRepository branchRepository;
    private final SupplyRepository supplyRepository;
    private final SupplyProductRepository supplyProductRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final UnitMasterRepository unitMasterRepository;

    public SupplyGrnAnalyticsResponse getAnalytics(Long branchId,
                                                   LocalDateTime from,
                                                   LocalDateTime to,
                                                   String dateRange,
                                                   int supplierLimit,
                                                   int itemLimit) {
        Range range = resolveRange(from, to, dateRange);
        int safeSupplierLimit = sanitizeLimit(supplierLimit, 10);
        int safeItemLimit = sanitizeLimit(itemLimit, 10);
        AnalyticsData data = loadData(branchId, range);

        return SupplyGrnAnalyticsResponse.builder()
                .context(buildContext(data.branch()))
                .filters(AnalyticsFilters.builder()
                        .dateRange(resolveDateRangeLabel(dateRange, from, to))
                        .from(range.from())
                        .to(range.to())
                        .timezone(data.branch().getTimezone())
                        .supplierLimit(safeSupplierLimit)
                        .itemLimit(safeItemLimit)
                        .build())
                .emptyState(buildEmptyState(data))
                .kpis(buildSummary(data))
                .receivingTrend(buildTrend(data))
                .receivingStatus(buildReceivingStatus(data))
                .paymentStatus(buildPaymentStatus(data))
                .completion(buildCompletion(data))
                .supplierReceiving(buildSupplierReceiving(data, safeSupplierLimit))
                .topReceivedItems(buildTopReceivedItems(data, safeItemLimit))
                .build();
    }

    public GrnSummary getSummary(Long branchId,
                                 LocalDateTime from,
                                 LocalDateTime to,
                                 String dateRange) {
        return buildSummary(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<ReceivingTrendBucket> getTrend(Long branchId,
                                               LocalDateTime from,
                                               LocalDateTime to,
                                               String dateRange) {
        return buildTrend(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public ReceivingStatus getReceivingStatus(Long branchId,
                                              LocalDateTime from,
                                              LocalDateTime to,
                                              String dateRange) {
        return buildReceivingStatus(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public GrnPaymentStatus getPaymentStatus(Long branchId,
                                             LocalDateTime from,
                                             LocalDateTime to,
                                             String dateRange) {
        return buildPaymentStatus(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public ReceivingCompletion getCompletion(Long branchId,
                                             LocalDateTime from,
                                             LocalDateTime to,
                                             String dateRange) {
        return buildCompletion(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<SupplierReceivingEntry> getSupplierReceiving(Long branchId,
                                                             LocalDateTime from,
                                                             LocalDateTime to,
                                                             String dateRange,
                                                             int limit) {
        return buildSupplierReceiving(loadData(branchId, resolveRange(from, to, dateRange)),
                sanitizeLimit(limit, 10));
    }

    public List<TopReceivedItem> getTopReceivedItems(Long branchId,
                                                     LocalDateTime from,
                                                     LocalDateTime to,
                                                     String dateRange,
                                                     int limit) {
        return buildTopReceivedItems(loadData(branchId, resolveRange(from, to, dateRange)),
                sanitizeLimit(limit, 10));
    }

    private AnalyticsData loadData(Long branchId, Range range) {
        Branch branch = requireBranch(branchId);
        List<Supply> supplies = supplyRepository
                .findByBranchIdAndSupplyDateBetweenOrderBySupplyDateDesc(branchId, range.from(), range.to())
                .stream()
                .filter(this::isRelevantSupply)
                .toList();
        List<Long> supplyIds = supplies.stream()
                .map(Supply::getSupplyId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<SupplyProduct>> productsBySupplyId = supplyIds.isEmpty()
                ? Map.of()
                : supplyProductRepository.findBySupplyIdIn(supplyIds).stream()
                .collect(Collectors.groupingBy(SupplyProduct::getSupplyId));

        Set<Long> supplierIds = supplies.stream()
                .map(Supply::getSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Supplier> suppliersById = new HashMap<>();
        supplierRepository.findAllById(supplierIds)
                .forEach(supplier -> suppliersById.put(supplier.getSupplierId(), supplier));

        List<SupplyProduct> products = productsBySupplyId.values().stream()
                .flatMap(Collection::stream)
                .toList();
        Set<Long> itemIds = products.stream()
                .map(SupplyProduct::getItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Item> itemsById = new HashMap<>();
        itemRepository.findAllById(itemIds)
                .forEach(item -> itemsById.put(item.getItemId(), item));

        Set<Long> unitIds = products.stream()
                .map(SupplyProduct::getUnitId)
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

        Set<Long> poIds = supplies.stream()
                .map(Supply::getPoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<PurchaseOrderItem>> poItemsByPoId = poIds.isEmpty()
                ? Map.of()
                : purchaseOrderItemRepository.findByPoIdIn(poIds).stream()
                .collect(Collectors.groupingBy(PurchaseOrderItem::getPoId));

        Map<Long, SupplyMetrics> metricsBySupplyId = supplies.stream()
                .collect(Collectors.toMap(
                        Supply::getSupplyId,
                        supply -> buildSupplyMetrics(supply,
                                productsBySupplyId.getOrDefault(supply.getSupplyId(), List.of()),
                                supply.getPoId() == null
                                        ? List.of()
                                        : poItemsByPoId.getOrDefault(supply.getPoId(), List.of())),
                        (left, right) -> left));

        return new AnalyticsData(
                branch,
                range,
                supplies,
                productsBySupplyId,
                metricsBySupplyId,
                suppliersById,
                itemsById,
                itemUnitsById,
                masterUnitsById);
    }

    private PageContext buildContext(Branch branch) {
        return PageContext.builder()
                .branchId(branch.getBranchId())
                .branchName(branch.getName())
                .title("Supplies / GRN Analytics")
                .subtitle("Receiving, inventory costs, supplier settlement and GRN health")
                .backRoute("/supplies")
                .build();
    }

    private EmptyState buildEmptyState(AnalyticsData data) {
        boolean empty = data.supplies().isEmpty();
        return EmptyState.builder()
                .empty(empty)
                .message(empty ? "No supplies or GRNs are available for the selected period." : null)
                .build();
    }

    private GrnSummary buildSummary(AnalyticsData data) {
        List<SupplyMetrics> metrics = data.metricsBySupplyId().values().stream().toList();
        BigDecimal receivedValue = metrics.stream()
                .map(SupplyMetrics::receivedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal receivedQty = metrics.stream()
                .map(SupplyMetrics::receivedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = metrics.stream()
                .map(SupplyMetrics::paidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balanceDue = metrics.stream()
                .map(SupplyMetrics::balanceDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal damagedQty = metrics.stream()
                .map(SupplyMetrics::damagedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long pendingPartial = metrics.stream()
                .filter(metric -> metric.receivingStatus() == GrnReceivingStatus.PENDING
                        || metric.receivingStatus() == GrnReceivingStatus.PARTIAL)
                .count();

        return GrnSummary.builder()
                .totalGrns(data.supplies().size())
                .receivedValue(receivedValue)
                .receivedQty(receivedQty)
                .pendingPartial(pendingPartial)
                .paidAmount(paidAmount)
                .balanceDue(balanceDue)
                .damagedQty(damagedQty)
                .currency(CURRENCY)
                .quantityUnit(DEFAULT_QTY_UNIT)
                .build();
    }

    private List<ReceivingTrendBucket> buildTrend(AnalyticsData data) {
        List<TrendAccumulator> buckets = createBuckets(data.range().from(), data.range().to());
        for (Supply supply : data.supplies()) {
            SupplyMetrics metrics = data.metricsBySupplyId().get(supply.getSupplyId());
            if (metrics == null) {
                continue;
            }
            bucketFor(buckets, supply.getSupplyDate())
                    .ifPresent(bucket -> bucket.add(metrics));
        }
        return buckets.stream()
                .map(TrendAccumulator::toResponse)
                .toList();
    }

    private ReceivingStatus buildReceivingStatus(AnalyticsData data) {
        long completed = data.metricsBySupplyId().values().stream()
                .filter(metric -> metric.receivingStatus() == GrnReceivingStatus.COMPLETED)
                .count();
        long partial = data.metricsBySupplyId().values().stream()
                .filter(metric -> metric.receivingStatus() == GrnReceivingStatus.PARTIAL)
                .count();
        long pending = data.metricsBySupplyId().values().stream()
                .filter(metric -> metric.receivingStatus() == GrnReceivingStatus.PENDING)
                .count();
        BigDecimal total = BigDecimal.valueOf(data.supplies().size());
        List<ReceivingStatusRow> rows = List.of(
                statusRow("COMPLETED", completed, total),
                statusRow("PARTIAL", partial, total),
                statusRow("PENDING", pending, total));

        return ReceivingStatus.builder()
                .totalGrns(data.supplies().size())
                .completed(completed)
                .partial(partial)
                .pending(pending)
                .completionPercentage(percent(BigDecimal.valueOf(completed), total))
                .rows(rows)
                .build();
    }

    private GrnPaymentStatus buildPaymentStatus(AnalyticsData data) {
        BigDecimal totalAmount = data.metricsBySupplyId().values().stream()
                .map(SupplyMetrics::payableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, PaymentAccumulator> byStatus = new LinkedHashMap<>();
        byStatus.put("PAID", new PaymentAccumulator("PAID"));
        byStatus.put("PARTIAL", new PaymentAccumulator("PARTIAL"));
        byStatus.put("UNPAID", new PaymentAccumulator("UNPAID"));

        for (SupplyMetrics metrics : data.metricsBySupplyId().values()) {
            String status = normalizePaymentStatus(metrics.paymentStatus());
            byStatus.computeIfAbsent(status, PaymentAccumulator::new).add(metrics);
        }

        return GrnPaymentStatus.builder()
                .totalAmount(totalAmount)
                .currency(CURRENCY)
                .segments(byStatus.values().stream()
                        .map(accumulator -> accumulator.toResponse(totalAmount))
                        .toList())
                .build();
    }

    private ReceivingCompletion buildCompletion(AnalyticsData data) {
        BigDecimal expected = data.metricsBySupplyId().values().stream()
                .map(SupplyMetrics::expectedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = data.metricsBySupplyId().values().stream()
                .map(SupplyMetrics::receivedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = expected.subtract(received).max(BigDecimal.ZERO);
        BigDecimal completionPercentage = percent(received, expected);
        if (completionPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            completionPercentage = BigDecimal.valueOf(100);
        }

        return ReceivingCompletion.builder()
                .expectedQty(expected)
                .receivedQty(received)
                .remainingQty(remaining)
                .completionPercentage(completionPercentage)
                .quantityUnit(DEFAULT_QTY_UNIT)
                .build();
    }

    private List<SupplierReceivingEntry> buildSupplierReceiving(AnalyticsData data, int limit) {
        Map<Long, SupplierAccumulator> suppliers = new HashMap<>();
        for (Supply supply : data.supplies()) {
            SupplyMetrics metrics = data.metricsBySupplyId().get(supply.getSupplyId());
            if (metrics == null) {
                continue;
            }
            suppliers.computeIfAbsent(supply.getSupplierId(), SupplierAccumulator::new)
                    .add(supply, metrics);
        }

        BigDecimal maxValue = suppliers.values().stream()
                .map(SupplierAccumulator::receivedValue)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        List<SupplierAccumulator> ranked = suppliers.values().stream()
                .sorted(Comparator.comparing(SupplierAccumulator::receivedValue).reversed())
                .limit(limit)
                .toList();

        List<SupplierReceivingEntry> rows = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            SupplierAccumulator accumulator = ranked.get(index);
            Supplier supplier = data.suppliersById().get(accumulator.supplierId);
            rows.add(accumulator.toResponse(index + 1, supplier, maxValue));
        }
        return rows;
    }

    private List<TopReceivedItem> buildTopReceivedItems(AnalyticsData data, int limit) {
        Map<Long, ItemAccumulator> items = new HashMap<>();
        for (List<SupplyProduct> products : data.productsBySupplyId().values()) {
            for (SupplyProduct product : products) {
                items.computeIfAbsent(product.getItemId(), ItemAccumulator::new)
                        .add(product, unitName(data, product.getUnitId()));
            }
        }

        List<ItemAccumulator> ranked = items.values().stream()
                .sorted(Comparator.comparing(ItemAccumulator::receivedQty).reversed()
                        .thenComparing(ItemAccumulator::receivedValue, Comparator.reverseOrder()))
                .limit(limit)
                .toList();

        List<TopReceivedItem> rows = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            ItemAccumulator accumulator = ranked.get(index);
            Item item = data.itemsById().get(accumulator.itemId);
            rows.add(accumulator.toResponse(index + 1, item));
        }
        return rows;
    }

    private SupplyMetrics buildSupplyMetrics(Supply supply,
                                             List<SupplyProduct> products,
                                             List<PurchaseOrderItem> poItems) {
        BigDecimal receivedValue = products.stream()
                .map(product -> lineValue(product).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal receivedQty = products.stream()
                .map(product -> nvl(product.getQuantityReceivedBase()).compareTo(BigDecimal.ZERO) > 0
                        ? nvl(product.getQuantityReceivedBase())
                        : nvl(product.getQuantityReceived()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal damagedQty = products.stream()
                .map(product -> nvl(product.getQtyDamaged()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payableAmount = nvl(supply.getPayableAmount()).compareTo(BigDecimal.ZERO) > 0
                ? nvl(supply.getPayableAmount())
                : nvl(supply.getTotal()).compareTo(BigDecimal.ZERO) > 0 ? nvl(supply.getTotal()) : receivedValue;
        BigDecimal paidAmount = nvl(supply.getPaidAmount());
        BigDecimal balanceDue = supply.getBalanceAmount() != null
                ? nvl(supply.getBalanceAmount())
                : payableAmount.subtract(paidAmount);
        BigDecimal expectedQty = expectedQty(products, poItems, receivedQty);
        GrnReceivingStatus receivingStatus = receivingStatus(supply, expectedQty, receivedQty);

        return new SupplyMetrics(
                supply.getSupplyId(),
                receivedValue,
                receivedQty,
                expectedQty,
                payableAmount,
                paidAmount,
                balanceDue.max(BigDecimal.ZERO),
                damagedQty,
                receivingStatus,
                normalizePaymentStatus(supply.getPaymentStatus()));
    }

    private BigDecimal expectedQty(List<SupplyProduct> products,
                                   List<PurchaseOrderItem> poItems,
                                   BigDecimal receivedQty) {
        if (poItems == null || poItems.isEmpty()) {
            return receivedQty;
        }
        BigDecimal expected = BigDecimal.ZERO;
        Set<String> matchedPoLineKeys = new HashSet<>();
        for (SupplyProduct product : products) {
            String key = poLineKey(product.getItemId(), product.getVariantId(), product.getUnitId());
            Optional<PurchaseOrderItem> match = poItems.stream()
                    .filter(poItem -> key.equals(poLineKey(poItem.getItemId(), poItem.getVariantId(), poItem.getUnitId())))
                    .findFirst();
            if (match.isPresent() && matchedPoLineKeys.add(key)) {
                expected = expected.add(nvl(match.get().getOrderedQty()));
            } else {
                expected = expected.add(nvl(product.getQuantityReceived()));
            }
        }
        return expected.compareTo(BigDecimal.ZERO) > 0 ? expected : receivedQty;
    }

    private GrnReceivingStatus receivingStatus(Supply supply, BigDecimal expectedQty, BigDecimal receivedQty) {
        String status = normalizeStatus(supply.getStatus());
        if ("COMPLETED".equals(status) || "FULLY_RECEIVED".equals(status)) {
            return GrnReceivingStatus.COMPLETED;
        }
        if (receivedQty.compareTo(BigDecimal.ZERO) <= 0 || "PENDING".equals(status)) {
            return GrnReceivingStatus.PENDING;
        }
        if (expectedQty.compareTo(BigDecimal.ZERO) > 0 && receivedQty.compareTo(expectedQty) >= 0) {
            return GrnReceivingStatus.COMPLETED;
        }
        return GrnReceivingStatus.PARTIAL;
    }

    private ReceivingStatusRow statusRow(String status, long count, BigDecimal total) {
        return ReceivingStatusRow.builder()
                .status(status)
                .count(count)
                .percentage(percent(BigDecimal.valueOf(count), total))
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
            case "7D", "30D", "3M", "6M", "CUSTOM" -> normalized;
            default -> DEFAULT_DATE_RANGE;
        };
    }

    private void validateDateRange(LocalDateTime from, LocalDateTime to) {
        if (!to.isAfter(from) && !to.isEqual(from)) {
            throw new IllegalArgumentException("to must be after or equal to from");
        }
    }

    private boolean isRelevantSupply(Supply supply) {
        String status = normalizeStatus(supply.getStatus());
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

    private BigDecimal lineValue(SupplyProduct product) {
        if (product.getLineTotal() != null) {
            return product.getLineTotal();
        }
        return nvl(product.getQuantityReceived()).multiply(nvl(product.getCostPrice()));
    }

    private String unitName(AnalyticsData data, Long unitId) {
        ItemUnit itemUnit = data.itemUnitsById().get(unitId);
        if (itemUnit == null) {
            return null;
        }
        UnitMaster masterUnit = itemUnit.getMasterUnitId() != null
                ? data.masterUnitsById().get(itemUnit.getMasterUnitId())
                : null;
        return masterUnit != null ? masterUnit.getName() : itemUnit.getUnitName();
    }

    private String poLineKey(Long itemId, Long variantId, Long unitId) {
        return itemId + ":" + (variantId == null ? "null" : variantId) + ":" + unitId;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return status.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private String normalizePaymentStatus(String status) {
        String normalized = normalizeStatus(status);
        return switch (normalized) {
            case "PAID" -> "PAID";
            case "PARTIAL", "PARTIALLY_PAID" -> "PARTIAL";
            default -> "UNPAID";
        };
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

    private enum GrnReceivingStatus {
        COMPLETED,
        PARTIAL,
        PENDING
    }

    private record Range(LocalDateTime from, LocalDateTime to) {
    }

    private record AnalyticsData(Branch branch,
                                 Range range,
                                 List<Supply> supplies,
                                 Map<Long, List<SupplyProduct>> productsBySupplyId,
                                 Map<Long, SupplyMetrics> metricsBySupplyId,
                                 Map<Long, Supplier> suppliersById,
                                 Map<Long, Item> itemsById,
                                 Map<Long, ItemUnit> itemUnitsById,
                                 Map<Long, UnitMaster> masterUnitsById) {
    }

    private record SupplyMetrics(Long supplyId,
                                 BigDecimal receivedValue,
                                 BigDecimal receivedQty,
                                 BigDecimal expectedQty,
                                 BigDecimal payableAmount,
                                 BigDecimal paidAmount,
                                 BigDecimal balanceDue,
                                 BigDecimal damagedQty,
                                 GrnReceivingStatus receivingStatus,
                                 String paymentStatus) {
    }

    private static class TrendAccumulator {
        private final String key;
        private final String label;
        private final LocalDateTime from;
        private final LocalDateTime to;
        private final int index;
        private final Set<Long> supplyIds = new HashSet<>();
        private BigDecimal receivedValue = BigDecimal.ZERO;
        private BigDecimal receivedQty = BigDecimal.ZERO;

        private TrendAccumulator(String key, String label, LocalDateTime from, LocalDateTime to, int index) {
            this.key = key;
            this.label = label;
            this.from = from;
            this.to = to;
            this.index = index;
        }

        private void add(SupplyMetrics metrics) {
            supplyIds.add(metrics.supplyId());
            receivedValue = receivedValue.add(metrics.receivedValue());
            receivedQty = receivedQty.add(metrics.receivedQty());
        }

        private ReceivingTrendBucket toResponse() {
            return ReceivingTrendBucket.builder()
                    .bucketKey(key != null ? key : "bucket-" + index)
                    .label(label)
                    .bucketFrom(from)
                    .bucketTo(to)
                    .receivedValue(receivedValue)
                    .receivedQty(receivedQty)
                    .grnCount(supplyIds.size())
                    .build();
        }
    }

    private class PaymentAccumulator {
        private final String status;
        private BigDecimal amount = BigDecimal.ZERO;
        private long grnCount;

        private PaymentAccumulator(String status) {
            this.status = status;
        }

        private void add(SupplyMetrics metrics) {
            amount = amount.add(metrics.payableAmount());
            grnCount++;
        }

        private PaymentStatusSegment toResponse(BigDecimal totalAmount) {
            return PaymentStatusSegment.builder()
                    .status(status)
                    .amount(amount)
                    .percentage(percent(amount, totalAmount))
                    .grnCount(grnCount)
                    .build();
        }
    }

    private class SupplierAccumulator {
        private final Long supplierId;
        private final Set<Long> supplyIds = new HashSet<>();
        private BigDecimal receivedValue = BigDecimal.ZERO;
        private BigDecimal receivedQty = BigDecimal.ZERO;

        private SupplierAccumulator(Long supplierId) {
            this.supplierId = supplierId;
        }

        private void add(Supply supply, SupplyMetrics metrics) {
            if (supplyIds.add(supply.getSupplyId())) {
                receivedValue = receivedValue.add(metrics.receivedValue());
                receivedQty = receivedQty.add(metrics.receivedQty());
            }
        }

        private BigDecimal receivedValue() {
            return receivedValue;
        }

        private SupplierReceivingEntry toResponse(int rank, Supplier supplier, BigDecimal maxValue) {
            return SupplierReceivingEntry.builder()
                    .rank(rank)
                    .supplierId(supplierId)
                    .supplierName(supplier != null ? supplier.getName() : "Unknown Supplier")
                    .receivedValue(receivedValue)
                    .receivedQty(receivedQty)
                    .grnCount(supplyIds.size())
                    .barPercent(percent(receivedValue, maxValue))
                    .currency(CURRENCY)
                    .build();
        }
    }

    private static class ItemAccumulator {
        private final Long itemId;
        private BigDecimal receivedQty = BigDecimal.ZERO;
        private BigDecimal receivedValue = BigDecimal.ZERO;
        private final Map<String, Long> unitCounts = new LinkedHashMap<>();

        private ItemAccumulator(Long itemId) {
            this.itemId = itemId;
        }

        private void add(SupplyProduct product, String unitName) {
            receivedQty = receivedQty.add(product.getQuantityReceivedBase() != null
                    ? product.getQuantityReceivedBase()
                    : product.getQuantityReceived() != null ? product.getQuantityReceived() : BigDecimal.ZERO);
            receivedValue = receivedValue.add(product.getLineTotal() != null
                    ? product.getLineTotal()
                    : nvlStatic(product.getQuantityReceived()).multiply(nvlStatic(product.getCostPrice())));
            if (unitName != null && !unitName.isBlank()) {
                unitCounts.merge(unitName, 1L, Long::sum);
            }
        }

        private BigDecimal receivedQty() {
            return receivedQty;
        }

        private BigDecimal receivedValue() {
            return receivedValue;
        }

        private TopReceivedItem toResponse(int rank, Item item) {
            return TopReceivedItem.builder()
                    .rank(rank)
                    .productId(itemId)
                    .productName(item != null ? item.getName() : "Unknown Product")
                    .sku(item != null ? item.getSku() : null)
                    .receivedQty(receivedQty)
                    .receivedValue(receivedValue)
                    .unitName(mostFrequent(unitCounts))
                    .build();
        }

        private static BigDecimal nvlStatic(BigDecimal value) {
            return value != null ? value : BigDecimal.ZERO;
        }

        private static String mostFrequent(Map<String, Long> counts) {
            return counts.entrySet().stream()
                    .max(Map.Entry.<String, Long>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey)
                    .orElse(DEFAULT_QTY_UNIT);
        }
    }
}
