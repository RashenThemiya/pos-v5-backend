package com.pos.system.service;

import com.pos.system.dto.dashboard.MainDashboardResponse;
import com.pos.system.dto.dashboard.MainDashboardResponse.*;
import com.pos.system.model.auth.Branch;
import com.pos.system.model.cash.CashSession;
import com.pos.system.model.cash.Counter;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.promotion.Promotion;
import com.pos.system.model.promotion.PromotionRedemption;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.model.sale.SalesReturn;
import com.pos.system.model.stock.StockBatch;
import com.pos.system.model.supplier.PurchaseOrder;
import com.pos.system.model.supplier.PurchaseOrderItem;
import com.pos.system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MainDashboardService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    private final BranchRepository branchRepository;
    private final CustomerOrderRepository orderRepository;
    private final SalesReturnRepository salesReturnRepository;
    private final ItemRepository itemRepository;
    private final StockBatchRepository stockBatchRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final CounterRepository counterRepository;
    private final CashSessionRepository cashSessionRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository promotionRedemptionRepository;

    public MainDashboardResponse getOverview(LocalDateTime from,
                                             LocalDateTime to,
                                             List<Long> branchIds,
                                             String metric) {
        validateDateRange(from, to);
        List<Branch> branches = resolveBranches(branchIds);
        Set<Long> scopedBranchIds = branchIdSet(branches);

        SummaryMetrics summary = getSummary(from, to, branchIds);
        InventoryHealth inventoryHealth = getInventoryHealth(branchIds);
        ProcurementMetrics procurement = getProcurement(branchIds);

        return MainDashboardResponse.builder()
                .periodFrom(from)
                .periodTo(to)
                .branchIds(List.copyOf(scopedBranchIds))
                .summary(summary)
                .inventory(InventoryCards.builder()
                        .inventoryValue(calculateInventoryValue(scopedBranchIds))
                        .lowStockItems(inventoryHealth.getLowStockItems())
                        .outOfStockItems(inventoryHealth.getOutOfStockItems())
                        .openPurchaseOrders(procurement.getOpenPurchaseOrders())
                        .build())
                .salesPerformance(getSalesPerformance(from, to, branchIds, metric))
                .branchPerformance(getBranchPerformance(from, to, branchIds))
                .branchStatus(getBranchStatus(branchIds))
                .inventoryHealth(inventoryHealth)
                .procurement(procurement)
                .returns(getReturns(from, to, branchIds))
                .operations(getOperations(from, to, branchIds))
                .promotions(getPromotions(from, to, branchIds))
                .build();
    }

    public SummaryMetrics getSummary(LocalDateTime from,
                                     LocalDateTime to,
                                     List<Long> branchIds) {
        validateDateRange(from, to);
        Set<Long> scopedBranchIds = branchIdSet(resolveBranches(branchIds));
        List<CustomerOrder> orders = validOrdersInRange(from, to, scopedBranchIds);
        List<SalesReturn> returns = validReturnsInRange(from, to, scopedBranchIds);

        Range previousRange = previousRange(from, to);
        List<CustomerOrder> previousOrders = validOrdersInRange(
                previousRange.from(), previousRange.to(), scopedBranchIds);
        List<SalesReturn> previousReturns = validReturnsInRange(
                previousRange.from(), previousRange.to(), scopedBranchIds);

        BigDecimal totalSales = sumOrderTotals(orders);
        BigDecimal refundValue = sumRefunds(returns);
        BigDecimal netSales = totalSales.subtract(refundValue);

        BigDecimal previousTotalSales = sumOrderTotals(previousOrders);
        BigDecimal previousRefundValue = sumRefunds(previousReturns);
        BigDecimal previousNetSales = previousTotalSales.subtract(previousRefundValue);

        return SummaryMetrics.builder()
                .totalSales(totalSales)
                .totalSalesChangePercent(changePercent(totalSales, previousTotalSales))
                .netSales(netSales)
                .netSalesChangePercent(changePercent(netSales, previousNetSales))
                .orders(orders.size())
                .ordersChangePercent(changePercent(BigDecimal.valueOf(orders.size()),
                        BigDecimal.valueOf(previousOrders.size())))
                .returns(returns.size())
                .returnsChangePercent(changePercent(BigDecimal.valueOf(returns.size()),
                        BigDecimal.valueOf(previousReturns.size())))
                .build();
    }

    public SalesPerformance getSalesPerformance(LocalDateTime from,
                                                LocalDateTime to,
                                                List<Long> branchIds,
                                                String metric) {
        validateDateRange(from, to);
        Set<Long> scopedBranchIds = branchIdSet(resolveBranches(branchIds));
        List<CustomerOrder> orders = validOrdersInRange(from, to, scopedBranchIds);
        List<BucketAccumulator> accumulators = createBuckets(from, to);

        for (CustomerOrder order : orders) {
            LocalDateTime orderDate = order.getOrderDate();
            accumulators.stream()
                    .filter(bucket -> isWithin(orderDate, bucket.from, bucket.to))
                    .findFirst()
                    .ifPresent(bucket -> {
                        bucket.revenue = bucket.revenue.add(nvl(order.getTotal()));
                        bucket.orders++;
                    });
        }

        List<SalesBucket> buckets = accumulators.stream()
                .map(BucketAccumulator::toResponse)
                .toList();

        return SalesPerformance.builder()
                .selectedMetric(normalizeMetric(metric))
                .buckets(buckets)
                .peakRevenueBucket(buckets.stream()
                        .max(Comparator.comparing(SalesBucket::getRevenue))
                        .orElse(null))
                .peakOrdersBucket(buckets.stream()
                        .max(Comparator.comparingLong(SalesBucket::getOrders))
                        .orElse(null))
                .build();
    }

    public List<BranchPerformanceEntry> getBranchPerformance(LocalDateTime from,
                                                             LocalDateTime to,
                                                             List<Long> branchIds) {
        validateDateRange(from, to);
        List<Branch> branches = resolveBranches(branchIds);
        Set<Long> scopedBranchIds = branchIdSet(branches);
        Map<Long, Branch> branchById = branches.stream()
                .collect(Collectors.toMap(Branch::getBranchId, Function.identity()));

        Map<Long, List<CustomerOrder>> ordersByBranch = validOrdersInRange(from, to, scopedBranchIds).stream()
                .collect(Collectors.groupingBy(CustomerOrder::getBranchId));
        Map<Long, List<SalesReturn>> returnsByBranch = validReturnsInRange(from, to, scopedBranchIds).stream()
                .collect(Collectors.groupingBy(SalesReturn::getBranchId));

        return branchById.values().stream()
                .map(branch -> {
                    List<CustomerOrder> orders = ordersByBranch.getOrDefault(branch.getBranchId(), List.of());
                    List<SalesReturn> returns = returnsByBranch.getOrDefault(branch.getBranchId(), List.of());
                    BigDecimal totalSales = sumOrderTotals(orders);
                    BigDecimal refundValue = sumRefunds(returns);

                    return BranchPerformanceEntry.builder()
                            .branchId(branch.getBranchId())
                            .branchName(branch.getName())
                            .totalSales(totalSales)
                            .netSales(totalSales.subtract(refundValue))
                            .orders(orders.size())
                            .returns(returns.size())
                            .build();
                })
                .sorted(Comparator.comparing(BranchPerformanceEntry::getTotalSales).reversed())
                .toList();
    }

    public BranchStatus getBranchStatus(List<Long> branchIds) {
        List<Branch> branches = resolveBranches(branchIds);
        long active = branches.stream()
                .filter(branch -> Boolean.TRUE.equals(branch.getIsActive()))
                .count();

        return BranchStatus.builder()
                .totalBranches(branches.size())
                .activeBranches(active)
                .inactiveBranches(branches.size() - active)
                .build();
    }

    public InventoryHealth getInventoryHealth(List<Long> branchIds) {
        Set<Long> scopedBranchIds = branchIdSet(resolveBranches(branchIds));
        List<Item> items = itemRepository.findAll().stream()
                .filter(item -> scopedBranchIds.contains(item.getBranchId()))
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .toList();

        Map<Long, BigDecimal> qtyByItem = quantityByItem(scopedBranchIds);

        long outOfStock = items.stream()
                .filter(item -> qtyByItem.getOrDefault(item.getItemId(), BigDecimal.ZERO)
                        .compareTo(BigDecimal.ZERO) <= 0)
                .count();

        long lowStock = items.stream()
                .filter(item -> {
                    BigDecimal minStock = item.getMinStock();
                    if (minStock == null || minStock.compareTo(BigDecimal.ZERO) <= 0) {
                        return false;
                    }
                    BigDecimal quantity = qtyByItem.getOrDefault(item.getItemId(), BigDecimal.ZERO);
                    return quantity.compareTo(BigDecimal.ZERO) > 0 && quantity.compareTo(minStock) <= 0;
                })
                .count();

        long healthy = Math.max(items.size() - outOfStock - lowStock, 0);

        return InventoryHealth.builder()
                .totalItems(items.size())
                .healthyItems(healthy)
                .lowStockItems(lowStock)
                .outOfStockItems(outOfStock)
                .healthyPercent(percent(BigDecimal.valueOf(healthy), BigDecimal.valueOf(items.size())))
                .lowStockPercent(percent(BigDecimal.valueOf(lowStock), BigDecimal.valueOf(items.size())))
                .outOfStockPercent(percent(BigDecimal.valueOf(outOfStock), BigDecimal.valueOf(items.size())))
                .build();
    }

    public ProcurementMetrics getProcurement(List<Long> branchIds) {
        Set<Long> scopedBranchIds = branchIdSet(resolveBranches(branchIds));
        List<PurchaseOrder> purchaseOrders = purchaseOrderRepository.findAll().stream()
                .filter(po -> scopedBranchIds.contains(po.getBranchId()))
                .toList();

        List<Long> poIds = purchaseOrders.stream()
                .map(PurchaseOrder::getPoId)
                .toList();

        List<PurchaseOrderItem> items = poIds.isEmpty()
                ? List.of()
                : purchaseOrderItemRepository.findByPoIdIn(poIds);

        Map<Long, List<PurchaseOrderItem>> itemsByPo = items.stream()
                .collect(Collectors.groupingBy(PurchaseOrderItem::getPoId));

        List<PurchaseOrder> openPurchaseOrders = purchaseOrders.stream()
                .filter(this::isOpenPurchaseOrder)
                .toList();

        LocalDate today = LocalDate.now();
        long overdue = openPurchaseOrders.stream()
                .filter(po -> po.getExpectedDate() != null && po.getExpectedDate().isBefore(today))
                .count();

        BigDecimal outstandingValue = openPurchaseOrders.stream()
                .flatMap(po -> itemsByPo.getOrDefault(po.getPoId(), List.of()).stream())
                .map(item -> remainingQty(item).multiply(nvl(item.getUnitCostEst())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal orderedQuantity = purchaseOrders.stream()
                .filter(po -> !isCancelledPurchaseOrder(po))
                .flatMap(po -> itemsByPo.getOrDefault(po.getPoId(), List.of()).stream())
                .map(item -> nvl(item.getOrderedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal receivedQuantity = purchaseOrders.stream()
                .filter(po -> !isCancelledPurchaseOrder(po))
                .flatMap(po -> itemsByPo.getOrDefault(po.getPoId(), List.of()).stream())
                .map(item -> nvl(item.getReceivedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ProcurementMetrics.builder()
                .openPurchaseOrders(openPurchaseOrders.size())
                .overduePurchaseOrders(overdue)
                .outstandingValue(outstandingValue)
                .orderedQuantity(orderedQuantity)
                .receivedQuantity(receivedQuantity)
                .receivingRatePercent(percent(receivedQuantity, orderedQuantity))
                .build();
    }

    public ReturnsMetrics getReturns(LocalDateTime from,
                                     LocalDateTime to,
                                     List<Long> branchIds) {
        validateDateRange(from, to);
        Set<Long> scopedBranchIds = branchIdSet(resolveBranches(branchIds));
        List<SalesReturn> returns = validReturnsInRange(from, to, scopedBranchIds);
        long orderCount = validOrdersInRange(from, to, scopedBranchIds).size();

        return ReturnsMetrics.builder()
                .returns(returns.size())
                .refundValue(sumRefunds(returns))
                .returnRatePercent(percent(BigDecimal.valueOf(returns.size()), BigDecimal.valueOf(orderCount)))
                .build();
    }

    public OperationsMetrics getOperations(LocalDateTime from,
                                           LocalDateTime to,
                                           List<Long> branchIds) {
        validateDateRange(from, to);
        Set<Long> scopedBranchIds = branchIdSet(resolveBranches(branchIds));
        List<Counter> counters = counterRepository.findAll().stream()
                .filter(counter -> scopedBranchIds.contains(counter.getBranchId()))
                .toList();
        Set<Long> counterIds = counters.stream()
                .map(Counter::getCounterId)
                .collect(Collectors.toSet());

        List<CashSession> sessions = cashSessionRepository.findAll().stream()
                .filter(session -> counterIds.contains(session.getCounterId()))
                .toList();

        BigDecimal cashVariance = sessions.stream()
                .filter(session -> isWithin(session.getClosedAt(), from, to))
                .map(session -> nvl(session.getCashDifference()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OperationsMetrics.builder()
                .activeCounters(counters.stream()
                        .filter(counter -> Boolean.TRUE.equals(counter.getIsActive()))
                        .count())
                .openSessions(sessions.stream()
                        .filter(session -> "OPEN".equalsIgnoreCase(safe(session.getStatus())))
                        .count())
                .transactions(validOrdersInRange(from, to, scopedBranchIds).size())
                .cashVariance(cashVariance)
                .build();
    }

    public PromotionMetrics getPromotions(LocalDateTime from,
                                          LocalDateTime to,
                                          List<Long> branchIds) {
        validateDateRange(from, to);
        List<Branch> branches = resolveBranches(branchIds);
        Set<Long> scopedBranchIds = branchIdSet(branches);
        LocalDateTime now = LocalDateTime.now();

        List<Promotion> promotions = promotionRepository.findAll().stream()
                .filter(promotion -> scopedBranchIds.contains(promotion.getBranchId()))
                .toList();

        long activePromotions = promotions.stream()
                .filter(promotion -> isActivePromotion(promotion, now))
                .count();

        List<PromotionBranchPerformance> performance = buildPromotionPerformance(
                from, to, branches, scopedBranchIds);

        return PromotionMetrics.builder()
                .activePromotions(activePromotions)
                .inactivePromotions(promotions.size() - activePromotions)
                .promotionSalesByBranch(performance)
                .build();
    }

    private List<PromotionBranchPerformance> buildPromotionPerformance(LocalDateTime from,
                                                                       LocalDateTime to,
                                                                       List<Branch> branches,
                                                                       Set<Long> scopedBranchIds) {
        Map<Long, CustomerOrder> ordersById = orderRepository.findAll().stream()
                .collect(Collectors.toMap(CustomerOrder::getOrderId, Function.identity(), (a, b) -> a));

        Map<Long, Set<Long>> orderIdsByBranch = new HashMap<>();
        Map<Long, BigDecimal> discountByBranch = new HashMap<>();
        Map<Long, Long> redemptionCountByBranch = new HashMap<>();

        for (PromotionRedemption redemption : promotionRedemptionRepository.findAll()) {
            CustomerOrder order = ordersById.get(redemption.getOrderId());
            LocalDateTime usedAt = redemption.getUsedAt() != null
                    ? redemption.getUsedAt()
                    : order != null ? order.getOrderDate() : null;

            if (!scopedBranchIds.contains(redemption.getBranchId())
                    || !isWithin(usedAt, from, to)
                    || order == null
                    || !isValidOrder(order)) {
                continue;
            }

            orderIdsByBranch.computeIfAbsent(redemption.getBranchId(), key -> new HashSet<>())
                    .add(redemption.getOrderId());
            discountByBranch.merge(redemption.getBranchId(), nvl(redemption.getDiscountAmount()), BigDecimal::add);
            redemptionCountByBranch.merge(redemption.getBranchId(), 1L, Long::sum);
        }

        return branches.stream()
                .map(branch -> {
                    BigDecimal promotionSales = orderIdsByBranch
                            .getOrDefault(branch.getBranchId(), Set.of())
                            .stream()
                            .map(ordersById::get)
                            .filter(Objects::nonNull)
                            .map(order -> nvl(order.getTotal()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return PromotionBranchPerformance.builder()
                            .branchId(branch.getBranchId())
                            .branchName(branch.getName())
                            .promotionSales(promotionSales)
                            .discountAmount(discountByBranch.getOrDefault(branch.getBranchId(), BigDecimal.ZERO))
                            .redemptionCount(redemptionCountByBranch.getOrDefault(branch.getBranchId(), 0L))
                            .build();
                })
                .sorted(Comparator.comparing(PromotionBranchPerformance::getPromotionSales).reversed())
                .toList();
    }

    private List<Branch> resolveBranches(List<Long> requestedBranchIds) {
        List<Branch> branches = branchRepository.findAll();
        List<Long> ids = requestedBranchIds == null
                ? List.of()
                : requestedBranchIds.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        if (ids.isEmpty()) {
            return branches;
        }

        Set<Long> requested = new LinkedHashSet<>(ids);
        List<Branch> scoped = branches.stream()
                .filter(branch -> requested.contains(branch.getBranchId()))
                .toList();

        Set<Long> found = scoped.stream()
                .map(Branch::getBranchId)
                .collect(Collectors.toSet());
        List<Long> missing = requested.stream()
                .filter(id -> !found.contains(id))
                .toList();

        if (!missing.isEmpty()) {
            throw new RuntimeException("Branch not found: " + missing);
        }

        return scoped;
    }

    private Set<Long> branchIdSet(List<Branch> branches) {
        return branches.stream()
                .map(Branch::getBranchId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<CustomerOrder> validOrdersInRange(LocalDateTime from,
                                                   LocalDateTime to,
                                                   Set<Long> branchIds) {
        return orderRepository.findAll().stream()
                .filter(order -> branchIds.contains(order.getBranchId()))
                .filter(this::isValidOrder)
                .filter(order -> isWithin(order.getOrderDate(), from, to))
                .toList();
    }

    private List<SalesReturn> validReturnsInRange(LocalDateTime from,
                                                  LocalDateTime to,
                                                  Set<Long> branchIds) {
        return salesReturnRepository.findAll().stream()
                .filter(salesReturn -> branchIds.contains(salesReturn.getBranchId()))
                .filter(this::isValidReturn)
                .filter(salesReturn -> isWithin(salesReturn.getReturnDate(), from, to))
                .toList();
    }

    private boolean isValidOrder(CustomerOrder order) {
        return "COMPLETED".equalsIgnoreCase(safe(order.getStatus()));
    }

    private boolean isValidReturn(SalesReturn salesReturn) {
        String status = safe(salesReturn.getStatus()).toUpperCase(Locale.ROOT);
        return !"CANCELLED".equals(status)
                && !"CANCELED".equals(status)
                && !"VOID".equals(status)
                && !"VOIDED".equals(status);
    }

    private boolean isOpenPurchaseOrder(PurchaseOrder purchaseOrder) {
        return !isCancelledPurchaseOrder(purchaseOrder) && !isCompletedPurchaseOrder(purchaseOrder);
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

    private boolean isActivePromotion(Promotion promotion, LocalDateTime now) {
        return Boolean.TRUE.equals(promotion.getIsActive())
                && (promotion.getStartAt() == null || !promotion.getStartAt().isAfter(now))
                && (promotion.getEndAt() == null || !promotion.getEndAt().isBefore(now));
    }

    private Map<Long, BigDecimal> quantityByItem(Set<Long> branchIds) {
        return stockBatchRepository.findAll().stream()
                .filter(batch -> branchIds.contains(batch.getBranchId()))
                .collect(Collectors.groupingBy(
                        StockBatch::getItemId,
                        Collectors.reducing(BigDecimal.ZERO, this::availableQty, BigDecimal::add)));
    }

    private BigDecimal calculateInventoryValue(Set<Long> branchIds) {
        return stockBatchRepository.findAll().stream()
                .filter(batch -> branchIds.contains(batch.getBranchId()))
                .map(batch -> availableQty(batch).multiply(nvl(batch.getCostPrice())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal availableQty(StockBatch batch) {
        if (batch.getAvailableQty() != null) {
            return batch.getAvailableQty();
        }
        return nvl(batch.getQtyRemaining());
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

    private BigDecimal remainingQty(PurchaseOrderItem item) {
        BigDecimal remaining = nvl(item.getOrderedQty()).subtract(nvl(item.getReceivedQty()));
        return remaining.max(BigDecimal.ZERO);
    }

    private List<BucketAccumulator> createBuckets(LocalDateTime from, LocalDateTime to) {
        long dayCount = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()) + 1;
        if (dayCount <= 31) {
            return createDailyBuckets(from, to);
        }
        if (dayCount <= 92) {
            return createWeeklyBuckets(from, to);
        }
        return createMonthlyBuckets(from, to);
    }

    private List<BucketAccumulator> createDailyBuckets(LocalDateTime from, LocalDateTime to) {
        List<BucketAccumulator> buckets = new ArrayList<>();
        int index = 1;
        for (LocalDate date = from.toLocalDate(); !date.isAfter(to.toLocalDate()); date = date.plusDays(1)) {
            LocalDateTime bucketFrom = date.atStartOfDay().isBefore(from) ? from : date.atStartOfDay();
            LocalDateTime bucketTo = date.plusDays(1).atStartOfDay().minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new BucketAccumulator(
                    date.toString(),
                    DAY_LABEL.format(date),
                    bucketFrom,
                    bucketTo,
                    index++));
        }
        return buckets;
    }

    private List<BucketAccumulator> createWeeklyBuckets(LocalDateTime from, LocalDateTime to) {
        List<BucketAccumulator> buckets = new ArrayList<>();
        LocalDateTime cursor = from;
        int index = 1;
        while (!cursor.isAfter(to)) {
            LocalDateTime bucketTo = cursor.plusDays(7).minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new BucketAccumulator(
                    "week-" + index,
                    "Week " + index,
                    cursor,
                    bucketTo,
                    index));
            cursor = bucketTo.plusNanos(1);
            index++;
        }
        return buckets;
    }

    private List<BucketAccumulator> createMonthlyBuckets(LocalDateTime from, LocalDateTime to) {
        List<BucketAccumulator> buckets = new ArrayList<>();
        LocalDateTime cursor = from;
        int index = 1;
        while (!cursor.isAfter(to)) {
            LocalDateTime nextMonth = cursor.toLocalDate()
                    .withDayOfMonth(1)
                    .plusMonths(1)
                    .atStartOfDay();
            LocalDateTime bucketTo = nextMonth.minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new BucketAccumulator(
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

    private void validateDateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("to must be after from");
        }
    }

    private Range previousRange(LocalDateTime from, LocalDateTime to) {
        Duration duration = Duration.between(from, to);
        LocalDateTime previousTo = from.minusNanos(1);
        return new Range(previousTo.minus(duration), previousTo);
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
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal changePercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current != null && current.compareTo(BigDecimal.ZERO) > 0
                    ? BigDecimal.valueOf(100)
                    : BigDecimal.ZERO;
        }
        return nvl(current).subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private String normalizeMetric(String metric) {
        return "ORDERS".equalsIgnoreCase(safe(metric)) ? "ORDERS" : "REVENUE";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record Range(LocalDateTime from, LocalDateTime to) {
    }

    private static class BucketAccumulator {
        private final String key;
        private final String label;
        private final LocalDateTime from;
        private final LocalDateTime to;
        private final int index;
        private BigDecimal revenue = BigDecimal.ZERO;
        private long orders;

        private BucketAccumulator(String key, String label, LocalDateTime from, LocalDateTime to, int index) {
            this.key = key;
            this.label = label;
            this.from = from;
            this.to = to;
            this.index = index;
        }

        private SalesBucket toResponse() {
            return SalesBucket.builder()
                    .bucketKey(key != null ? key : "bucket-" + index)
                    .label(label)
                    .bucketFrom(from)
                    .bucketTo(to)
                    .revenue(revenue)
                    .orders(orders)
                    .build();
        }
    }
}
