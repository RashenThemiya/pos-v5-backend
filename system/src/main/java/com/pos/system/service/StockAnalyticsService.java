package com.pos.system.service;

import com.pos.system.dto.stock.StockAnalyticsResponse;
import com.pos.system.dto.stock.StockAnalyticsResponse.*;
import com.pos.system.model.auth.Branch;
import com.pos.system.model.catalog.Category;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.model.sale.OrderProduct;
import com.pos.system.model.stock.StockBatch;
import com.pos.system.model.stock.StockMovement;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.CategoryRepository;
import com.pos.system.repository.CustomerOrderRepository;
import com.pos.system.repository.ItemRepository;
import com.pos.system.repository.OrderProductRepository;
import com.pos.system.repository.StockBatchRepository;
import com.pos.system.repository.StockMovementRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockAnalyticsService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final String DEFAULT_DATE_RANGE = "30D";
    private static final String CURRENCY = "Rs";
    private static final String DEFAULT_QTY_UNIT = "base units";
    private static final int DEFAULT_LIMIT = 10;
    private static final int DEFAULT_SLOW_WARNING_DAYS = 30;
    private static final int DEFAULT_SLOW_CRITICAL_DAYS = 90;

    private final Set<String> readNotificationIds = ConcurrentHashMap.newKeySet();

    private final BranchRepository branchRepository;
    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderProductRepository orderProductRepository;

    public StockAnalyticsResponse getAnalytics(Long branchId,
                                               LocalDateTime from,
                                               LocalDateTime to,
                                               String dateRange,
                                               int productLimit,
                                               int slowLimit,
                                               int slowWarningDays,
                                               int slowCriticalDays) {
        Range range = resolveRange(from, to, dateRange);
        int safeProductLimit = sanitizeLimit(productLimit, DEFAULT_LIMIT);
        int safeSlowLimit = sanitizeLimit(slowLimit, DEFAULT_LIMIT);
        SlowThresholds thresholds = resolveSlowThresholds(slowWarningDays, slowCriticalDays);
        AnalyticsData data = loadData(branchId, range);
        List<StockNotification> notifications = buildNotifications(data);

        return StockAnalyticsResponse.builder()
                .context(buildContext(data.branch()))
                .filters(AnalyticsFilters.builder()
                        .dateRange(resolveDateRangeLabel(dateRange, from, to))
                        .from(range.from())
                        .to(range.to())
                        .timezone(data.branch().getTimezone())
                        .productLimit(safeProductLimit)
                        .slowLimit(safeSlowLimit)
                        .slowWarningDays(thresholds.warningDays())
                        .slowCriticalDays(thresholds.criticalDays())
                        .build())
                .emptyState(buildEmptyState(data))
                .summary(buildSummary(data))
                .unreadNotificationCount(unreadCount(notifications))
                .notifications(notifications)
                .movement(buildMovement(data))
                .health(buildHealth(data))
                .inventoryValue(buildInventoryValue(data))
                .topSellingProducts(buildTopSellingProducts(data, safeProductLimit))
                .slowMovingProducts(buildSlowMovingProducts(data, safeSlowLimit, thresholds))
                .build();
    }

    public StockSummary getSummary(Long branchId) {
        return buildSummary(loadData(branchId, resolveRange(null, null, DEFAULT_DATE_RANGE)));
    }

    public List<MovementBucket> getMovement(Long branchId,
                                            LocalDateTime from,
                                            LocalDateTime to,
                                            String dateRange) {
        return buildMovement(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public InventoryHealth getHealth(Long branchId) {
        return buildHealth(loadData(branchId, resolveRange(null, null, DEFAULT_DATE_RANGE)));
    }

    public InventoryValue getInventoryValue(Long branchId,
                                            LocalDateTime from,
                                            LocalDateTime to,
                                            String dateRange) {
        return buildInventoryValue(loadData(branchId, resolveRange(from, to, dateRange)));
    }

    public List<TopSellingProduct> getTopSellingProducts(Long branchId,
                                                         LocalDateTime from,
                                                         LocalDateTime to,
                                                         String dateRange,
                                                         int limit) {
        return buildTopSellingProducts(loadData(branchId, resolveRange(from, to, dateRange)),
                sanitizeLimit(limit, DEFAULT_LIMIT));
    }

    public List<SlowMovingProduct> getSlowMovingProducts(Long branchId,
                                                         LocalDateTime from,
                                                         LocalDateTime to,
                                                         String dateRange,
                                                         int limit,
                                                         int slowWarningDays,
                                                         int slowCriticalDays) {
        return buildSlowMovingProducts(
                loadData(branchId, resolveRange(from, to, dateRange)),
                sanitizeLimit(limit, DEFAULT_LIMIT),
                resolveSlowThresholds(slowWarningDays, slowCriticalDays));
    }

    public List<StockNotification> getNotifications(Long branchId) {
        return buildNotifications(loadData(branchId, resolveRange(null, null, DEFAULT_DATE_RANGE)));
    }

    public NotificationReadResponse markAllNotificationsRead(Long branchId) {
        AnalyticsData data = loadData(branchId, resolveRange(null, null, DEFAULT_DATE_RANGE));
        List<StockNotification> current = buildNotifications(data);
        current.forEach(notification -> readNotificationIds.add(notification.getId()));
        List<StockNotification> updated = buildNotifications(data);
        return NotificationReadResponse.builder()
                .markedRead(current.size())
                .unreadNotificationCount(unreadCount(updated))
                .notifications(updated)
                .build();
    }

    private AnalyticsData loadData(Long branchId, Range range) {
        Branch branch = requireBranch(branchId);
        List<Item> items = itemRepository.findByBranchIdAndIsActive(branchId, true);
        List<StockBatch> batches = stockBatchRepository.findByBranchId(branchId);
        List<StockMovement> movements = stockMovementRepository.findByBranchIdOrderByCreatedAtDesc(branchId).stream()
                .filter(movement -> isWithin(movement.getCreatedAt(), range.from(), range.to()))
                .toList();

        List<CustomerOrder> rangeOrders = orderRepository.findByBranchIdAndOrderDateBetweenOrderByOrderDateDesc(
                        branchId, range.from(), range.to()).stream()
                .filter(this::isValidOrder)
                .toList();
        Map<Long, CustomerOrder> rangeOrdersById = rangeOrders.stream()
                .collect(Collectors.toMap(CustomerOrder::getOrderId, order -> order, (a, b) -> a));
        List<OrderProduct> rangeOrderProducts = loadOrderProducts(rangeOrdersById.keySet());

        Map<Long, CustomerOrder> ordersUpToEndById = orderRepository.findByBranchIdOrderByOrderDateDesc(branchId).stream()
                .filter(this::isValidOrder)
                .filter(order -> order.getOrderDate() != null && !order.getOrderDate().isAfter(range.to()))
                .collect(Collectors.toMap(CustomerOrder::getOrderId, order -> order, (a, b) -> a));
        List<OrderProduct> orderProductsUpToEnd = loadOrderProducts(ordersUpToEndById.keySet());

        Map<Long, Category> categoriesById = categoryRepository.findByBranchId(branchId).stream()
                .collect(Collectors.toMap(Category::getCategoryId, category -> category, (a, b) -> a));
        Map<Long, List<StockBatch>> batchesByItemId = batches.stream()
                .collect(Collectors.groupingBy(StockBatch::getItemId));
        Map<Long, BigDecimal> availableQtyByItemId = items.stream()
                .collect(Collectors.toMap(
                        Item::getItemId,
                        item -> batchesByItemId.getOrDefault(item.getItemId(), List.of()).stream()
                                .map(this::availableQty)
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        (a, b) -> a));
        Map<Long, BigDecimal> averageCostByItemId = buildAverageCostByItemId(batchesByItemId);

        return new AnalyticsData(
                branch,
                range,
                items,
                batches,
                batchesByItemId,
                availableQtyByItemId,
                averageCostByItemId,
                movements,
                rangeOrdersById,
                rangeOrderProducts,
                ordersUpToEndById,
                orderProductsUpToEnd,
                categoriesById);
    }

    private PageContext buildContext(Branch branch) {
        return PageContext.builder()
                .branchId(branch.getBranchId())
                .branchName(branch.getName())
                .title("Stock Analytics")
                .subtitle("Inventory performance and stock health")
                .backRoute("/stocks")
                .tabs(List.of("Overview", "Movement", "Health & Value", "Products"))
                .build();
    }

    private EmptyState buildEmptyState(AnalyticsData data) {
        boolean empty = data.items().isEmpty();
        return EmptyState.builder()
                .empty(empty)
                .message(empty ? "No active products are available for this branch." : null)
                .build();
    }

    private StockSummary buildSummary(AnalyticsData data) {
        return StockSummary.builder()
                .totalProducts(data.items().size())
                .lowStock(data.items().stream().filter(item -> stockStatus(data, item) == StockStatus.LOW_STOCK).count())
                .outOfStock(data.items().stream().filter(item -> stockStatus(data, item) == StockStatus.OUT_OF_STOCK).count())
                .totalAvailableQty(data.availableQtyByItemId().values().stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .quantityUnit(DEFAULT_QTY_UNIT)
                .build();
    }

    private List<MovementBucket> buildMovement(AnalyticsData data) {
        List<MovementAccumulator> buckets = createMovementBuckets(data.range().from(), data.range().to());
        for (StockMovement movement : data.movements()) {
            bucketFor(buckets, movement.getCreatedAt())
                    .ifPresent(bucket -> bucket.add(movement, movementCategory(movement.getMovementType())));
        }
        return buckets.stream()
                .map(MovementAccumulator::toResponse)
                .toList();
    }

    private InventoryHealth buildHealth(AnalyticsData data) {
        long healthy = 0;
        long low = 0;
        long out = 0;
        for (Item item : data.items()) {
            StockStatus status = stockStatus(data, item);
            if (status == StockStatus.OUT_OF_STOCK) {
                out++;
            } else if (status == StockStatus.LOW_STOCK) {
                low++;
            } else {
                healthy++;
            }
        }
        BigDecimal total = BigDecimal.valueOf(data.items().size());
        return InventoryHealth.builder()
                .totalProducts(data.items().size())
                .healthyCount(healthy)
                .lowStockCount(low)
                .outOfStockCount(out)
                .segments(List.of(
                        HealthSegment.builder()
                                .status("HEALTHY")
                                .count(healthy)
                                .percentage(percent(BigDecimal.valueOf(healthy), total))
                                .build(),
                        HealthSegment.builder()
                                .status("LOW_STOCK")
                                .count(low)
                                .percentage(percent(BigDecimal.valueOf(low), total))
                                .build(),
                        HealthSegment.builder()
                                .status("OUT_OF_STOCK")
                                .count(out)
                                .percentage(percent(BigDecimal.valueOf(out), total))
                                .build()))
                .build();
    }

    private InventoryValue buildInventoryValue(AnalyticsData data) {
        BigDecimal estimatedValue = data.batches().stream()
                .map(batch -> availableQty(batch).multiply(nvl(batch.getCostPrice())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal purchasedQty = BigDecimal.ZERO;
        BigDecimal purchasedValue = BigDecimal.ZERO;
        BigDecimal adjustmentQty = BigDecimal.ZERO;
        BigDecimal adjustmentValue = BigDecimal.ZERO;
        for (StockMovement movement : data.movements()) {
            MovementCategory category = movementCategory(movement.getMovementType());
            if (category == MovementCategory.RECEIVED) {
                BigDecimal qty = movementQtyAbs(movement);
                purchasedQty = purchasedQty.add(qty);
                purchasedValue = purchasedValue.add(qty.multiply(movementCost(data, movement)));
            } else if (category == MovementCategory.ADJUSTMENT) {
                BigDecimal qty = signedAdjustmentQty(movement);
                adjustmentQty = adjustmentQty.add(qty);
                adjustmentValue = adjustmentValue.add(qty.multiply(movementCost(data, movement)));
            }
        }

        BigDecimal soldQty = data.rangeOrderProducts().stream()
                .map(product -> nvl(product.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal soldValue = data.rangeOrderProducts().stream()
                .map(product -> nvl(product.getLineTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return InventoryValue.builder()
                .estimatedValue(estimatedValue)
                .purchasedValue(purchasedValue)
                .soldValue(soldValue)
                .adjustmentValue(adjustmentValue)
                .purchasedQty(purchasedQty)
                .soldQty(soldQty)
                .adjustmentQty(adjustmentQty)
                .valuationMethod("MOVING_AVERAGE_COST")
                .currency(CURRENCY)
                .build();
    }

    private List<TopSellingProduct> buildTopSellingProducts(AnalyticsData data, int limit) {
        Map<Long, ProductSalesAccumulator> products = new HashMap<>();
        for (OrderProduct product : data.rangeOrderProducts()) {
            products.computeIfAbsent(product.getItemId(), ProductSalesAccumulator::new)
                    .add(product);
        }
        BigDecimal maxQuantity = products.values().stream()
                .map(ProductSalesAccumulator::quantitySold)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        List<ProductSalesAccumulator> ranked = products.values().stream()
                .sorted(Comparator.comparing(ProductSalesAccumulator::quantitySold).reversed()
                        .thenComparing(ProductSalesAccumulator::salesValue, Comparator.reverseOrder()))
                .limit(limit)
                .toList();

        List<TopSellingProduct> rows = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            ProductSalesAccumulator accumulator = ranked.get(index);
            Item item = itemById(data, accumulator.itemId);
            Category category = categoryFor(data, item);
            rows.add(TopSellingProduct.builder()
                    .rank(index + 1)
                    .productId(accumulator.itemId)
                    .productName(item != null ? item.getName() : "Unknown Product")
                    .sku(item != null ? item.getSku() : null)
                    .categoryId(category != null ? category.getCategoryId() : null)
                    .categoryName(category != null ? category.getName() : null)
                    .quantitySold(accumulator.quantitySold())
                    .salesValue(accumulator.salesValue())
                    .barPercent(percent(accumulator.quantitySold(), maxQuantity))
                    .quantityUnit(DEFAULT_QTY_UNIT)
                    .build());
        }
        return rows;
    }

    private List<SlowMovingProduct> buildSlowMovingProducts(AnalyticsData data,
                                                            int limit,
                                                            SlowThresholds thresholds) {
        Map<Long, LocalDateTime> latestSaleByItemId = new HashMap<>();
        for (OrderProduct product : data.orderProductsUpToEnd()) {
            CustomerOrder order = data.ordersUpToEndById().get(product.getOrderId());
            if (order == null || order.getOrderDate() == null) {
                continue;
            }
            latestSaleByItemId.merge(product.getItemId(), order.getOrderDate(),
                    (left, right) -> left.isAfter(right) ? left : right);
        }

        List<SlowMovingAccumulator> ranked = data.items().stream()
                .map(item -> new SlowMovingAccumulator(item, latestSaleByItemId.get(item.getItemId())))
                .sorted(Comparator.comparing((SlowMovingAccumulator accumulator) -> rankDays(accumulator, data.range().to()))
                        .reversed()
                        .thenComparing(accumulator -> accumulator.item.getName(), String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .toList();

        List<SlowMovingProduct> rows = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            SlowMovingAccumulator accumulator = ranked.get(index);
            Item item = accumulator.item;
            Category category = categoryFor(data, item);
            Long days = accumulator.lastSoldAt == null
                    ? null
                    : Math.max(0, Duration.between(accumulator.lastSoldAt, data.range().to()).toDays());
            rows.add(SlowMovingProduct.builder()
                    .rank(index + 1)
                    .productId(item.getItemId())
                    .productName(item.getName())
                    .sku(item.getSku())
                    .categoryId(category != null ? category.getCategoryId() : null)
                    .categoryName(category != null ? category.getName() : null)
                    .lastSoldAt(accumulator.lastSoldAt)
                    .daysSinceLastSale(days)
                    .status(slowStatus(days, thresholds))
                    .currentStock(data.availableQtyByItemId().getOrDefault(item.getItemId(), BigDecimal.ZERO))
                    .asOf(data.range().to())
                    .build());
        }
        return rows;
    }

    private List<StockNotification> buildNotifications(AnalyticsData data) {
        List<StockNotification> notifications = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Item item : data.items()) {
            StockStatus status = stockStatus(data, item);
            if (status == StockStatus.OUT_OF_STOCK) {
                notifications.add(notification(
                        "stock-out-" + data.branch().getBranchId() + "-" + item.getItemId(),
                        "OUT_OF_STOCK",
                        "CRITICAL",
                        item.getName() + " is out of stock",
                        "Available stock is zero or below.",
                        now,
                        item));
            } else if (status == StockStatus.LOW_STOCK) {
                notifications.add(notification(
                        "stock-low-" + data.branch().getBranchId() + "-" + item.getItemId(),
                        "LOW_STOCK",
                        "WARNING",
                        item.getName() + " is low in stock",
                        "Available stock is at or below the reorder level.",
                        now,
                        item));
            }
        }

        return notifications.stream()
                .sorted(Comparator.comparing(StockNotification::getSeverity)
                        .thenComparing(StockNotification::getTitle, String.CASE_INSENSITIVE_ORDER))
                .limit(50)
                .toList();
    }

    private StockNotification notification(String id,
                                           String type,
                                           String severity,
                                           String title,
                                           String message,
                                           LocalDateTime createdAt,
                                           Item item) {
        return StockNotification.builder()
                .id(id)
                .type(type)
                .severity(severity)
                .title(title)
                .message(message)
                .read(readNotificationIds.contains(id))
                .createdAt(createdAt)
                .productId(item.getItemId())
                .targetRoute("/stocks?itemId=" + item.getItemId())
                .build();
    }

    private Branch requireBranch(Long branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found: " + branchId));
    }

    private List<OrderProduct> loadOrderProducts(Set<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return orderProductRepository.findByOrderIdIn(new ArrayList<>(orderIds));
    }

    private Map<Long, BigDecimal> buildAverageCostByItemId(Map<Long, List<StockBatch>> batchesByItemId) {
        Map<Long, BigDecimal> averageCostByItemId = new HashMap<>();
        for (Map.Entry<Long, List<StockBatch>> entry : batchesByItemId.entrySet()) {
            BigDecimal totalQty = entry.getValue().stream()
                    .map(this::availableQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalValue = entry.getValue().stream()
                    .map(batch -> availableQty(batch).multiply(nvl(batch.getCostPrice())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            averageCostByItemId.put(entry.getKey(), totalQty.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : totalValue.divide(totalQty, 4, RoundingMode.HALF_UP));
        }
        return averageCostByItemId;
    }

    private StockStatus stockStatus(AnalyticsData data, Item item) {
        BigDecimal current = data.availableQtyByItemId().getOrDefault(item.getItemId(), BigDecimal.ZERO);
        if (current.compareTo(BigDecimal.ZERO) <= 0) {
            return StockStatus.OUT_OF_STOCK;
        }
        BigDecimal threshold = nvl(item.getMinStock());
        if (threshold.compareTo(BigDecimal.ZERO) > 0 && current.compareTo(threshold) <= 0) {
            return StockStatus.LOW_STOCK;
        }
        return StockStatus.HEALTHY;
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
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("to must be after from");
        }
    }

    private SlowThresholds resolveSlowThresholds(int warningDays, int criticalDays) {
        int warning = warningDays > 0 ? warningDays : DEFAULT_SLOW_WARNING_DAYS;
        int critical = criticalDays > warning ? criticalDays : DEFAULT_SLOW_CRITICAL_DAYS;
        if (critical <= warning) {
            critical = warning + 1;
        }
        return new SlowThresholds(warning, critical);
    }

    private boolean isWithin(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        return value != null && !value.isBefore(from) && !value.isAfter(to);
    }

    private boolean isValidOrder(CustomerOrder order) {
        return "COMPLETED".equalsIgnoreCase(safe(order.getStatus()));
    }

    private MovementCategory movementCategory(String movementType) {
        String type = normalize(movementType);
        if (Set.of("PURCHASE_IN", "OPENING", "TRANSFER_IN", "SALE_RETURN", "SALE_CANCEL").contains(type)) {
            return MovementCategory.RECEIVED;
        }
        if (Set.of("SALE", "TRANSFER_OUT", "PURCHASE_RETURN_OUT").contains(type)) {
            return MovementCategory.SOLD;
        }
        if (Set.of(
                "AVAILABLE_TO_DAMAGED",
                "AVAILABLE_TO_EXPIRED",
                "DAMAGED_TO_AVAILABLE",
                "EXPIRED_TO_AVAILABLE",
                "AVAILABLE_OUT",
                "AVAILABLE_IN").contains(type)) {
            return MovementCategory.ADJUSTMENT;
        }
        return MovementCategory.OTHER;
    }

    private BigDecimal signedAdjustmentQty(StockMovement movement) {
        String type = normalize(movement.getMovementType());
        BigDecimal qty = movementQtyAbs(movement);
        if (Set.of("AVAILABLE_OUT", "AVAILABLE_TO_DAMAGED", "AVAILABLE_TO_EXPIRED").contains(type)) {
            return qty.negate();
        }
        if (Set.of("AVAILABLE_IN", "DAMAGED_TO_AVAILABLE", "EXPIRED_TO_AVAILABLE").contains(type)) {
            return qty;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal movementCost(AnalyticsData data, StockMovement movement) {
        if (movement.getUnitCost() != null) {
            return movement.getUnitCost();
        }
        return data.averageCostByItemId().getOrDefault(movement.getItemId(), BigDecimal.ZERO);
    }

    private BigDecimal movementQtyAbs(StockMovement movement) {
        return nvl(movement.getQuantity()).abs();
    }

    private BigDecimal availableQty(StockBatch batch) {
        return batch.getAvailableQty() != null ? batch.getAvailableQty() : nvl(batch.getQtyRemaining());
    }

    private List<MovementAccumulator> createMovementBuckets(LocalDateTime from, LocalDateTime to) {
        long dayCount = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()) + 1;
        if (dayCount <= 31) {
            return createDailyBuckets(from, to);
        }
        if (dayCount <= 92) {
            return createWeeklyBuckets(from, to);
        }
        return createMonthlyBuckets(from, to);
    }

    private List<MovementAccumulator> createDailyBuckets(LocalDateTime from, LocalDateTime to) {
        List<MovementAccumulator> buckets = new ArrayList<>();
        int index = 1;
        for (LocalDate date = from.toLocalDate(); !date.isAfter(to.toLocalDate()); date = date.plusDays(1)) {
            LocalDateTime bucketFrom = date.atStartOfDay().isBefore(from) ? from : date.atStartOfDay();
            LocalDateTime bucketTo = date.plusDays(1).atStartOfDay().minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new MovementAccumulator(date.toString(), DAY_LABEL.format(date), bucketFrom, bucketTo, index++));
        }
        return buckets;
    }

    private List<MovementAccumulator> createWeeklyBuckets(LocalDateTime from, LocalDateTime to) {
        List<MovementAccumulator> buckets = new ArrayList<>();
        LocalDateTime cursor = from;
        int index = 1;
        while (!cursor.isAfter(to)) {
            LocalDateTime bucketTo = cursor.plusDays(7).minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new MovementAccumulator("week-" + index, "Week " + index, cursor, bucketTo, index));
            cursor = bucketTo.plusNanos(1);
            index++;
        }
        return buckets;
    }

    private List<MovementAccumulator> createMonthlyBuckets(LocalDateTime from, LocalDateTime to) {
        List<MovementAccumulator> buckets = new ArrayList<>();
        LocalDateTime cursor = from;
        int index = 1;
        while (!cursor.isAfter(to)) {
            LocalDateTime nextMonth = cursor.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay();
            LocalDateTime bucketTo = nextMonth.minusNanos(1);
            if (bucketTo.isAfter(to)) {
                bucketTo = to;
            }
            buckets.add(new MovementAccumulator(
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

    private Optional<MovementAccumulator> bucketFor(List<MovementAccumulator> buckets, LocalDateTime value) {
        return buckets.stream()
                .filter(bucket -> isWithin(value, bucket.from, bucket.to))
                .findFirst();
    }

    private String slowStatus(Long days, SlowThresholds thresholds) {
        if (days == null) {
            return "NEVER_SOLD";
        }
        if (days >= thresholds.criticalDays()) {
            return "CRITICAL";
        }
        if (days >= thresholds.warningDays()) {
            return "WARNING";
        }
        return "MONITOR";
    }

    private long rankDays(SlowMovingAccumulator accumulator, LocalDateTime asOf) {
        return accumulator.lastSoldAt == null
                ? Long.MAX_VALUE
                : Math.max(0, Duration.between(accumulator.lastSoldAt, asOf).toDays());
    }

    private long unreadCount(List<StockNotification> notifications) {
        return notifications.stream().filter(notification -> !notification.isRead()).count();
    }

    private Item itemById(AnalyticsData data, Long itemId) {
        return data.items().stream()
                .filter(item -> item.getItemId().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    private Category categoryFor(AnalyticsData data, Item item) {
        return item != null && item.getCategoryId() != null
                ? data.categoriesById().get(item.getCategoryId())
                : null;
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private enum StockStatus {
        HEALTHY,
        LOW_STOCK,
        OUT_OF_STOCK
    }

    private enum MovementCategory {
        RECEIVED,
        SOLD,
        ADJUSTMENT,
        OTHER
    }

    private record Range(LocalDateTime from, LocalDateTime to) {
    }

    private record SlowThresholds(int warningDays, int criticalDays) {
    }

    private record AnalyticsData(Branch branch,
                                 Range range,
                                 List<Item> items,
                                 List<StockBatch> batches,
                                 Map<Long, List<StockBatch>> batchesByItemId,
                                 Map<Long, BigDecimal> availableQtyByItemId,
                                 Map<Long, BigDecimal> averageCostByItemId,
                                 List<StockMovement> movements,
                                 Map<Long, CustomerOrder> rangeOrdersById,
                                 List<OrderProduct> rangeOrderProducts,
                                 Map<Long, CustomerOrder> ordersUpToEndById,
                                 List<OrderProduct> orderProductsUpToEnd,
                                 Map<Long, Category> categoriesById) {
    }

    private class MovementAccumulator {
        private final String key;
        private final String label;
        private final LocalDateTime from;
        private final LocalDateTime to;
        private final int index;
        private BigDecimal receivedQty = BigDecimal.ZERO;
        private BigDecimal soldQty = BigDecimal.ZERO;
        private BigDecimal adjustmentQty = BigDecimal.ZERO;

        private MovementAccumulator(String key, String label, LocalDateTime from, LocalDateTime to, int index) {
            this.key = key;
            this.label = label;
            this.from = from;
            this.to = to;
            this.index = index;
        }

        private void add(StockMovement movement, MovementCategory category) {
            if (category == MovementCategory.RECEIVED) {
                receivedQty = receivedQty.add(movementQtyAbs(movement));
            } else if (category == MovementCategory.SOLD) {
                soldQty = soldQty.add(movementQtyAbs(movement));
            } else if (category == MovementCategory.ADJUSTMENT) {
                adjustmentQty = adjustmentQty.add(signedAdjustmentQty(movement));
            }
        }

        private MovementBucket toResponse() {
            return MovementBucket.builder()
                    .bucketKey(key != null ? key : "bucket-" + index)
                    .label(label)
                    .bucketFrom(from)
                    .bucketTo(to)
                    .receivedQty(receivedQty)
                    .soldQty(soldQty)
                    .adjustmentQty(adjustmentQty)
                    .build();
        }
    }

    private static class ProductSalesAccumulator {
        private final Long itemId;
        private BigDecimal quantitySold = BigDecimal.ZERO;
        private BigDecimal salesValue = BigDecimal.ZERO;

        private ProductSalesAccumulator(Long itemId) {
            this.itemId = itemId;
        }

        private void add(OrderProduct product) {
            quantitySold = quantitySold.add(product.getQuantity() != null ? product.getQuantity() : BigDecimal.ZERO);
            salesValue = salesValue.add(product.getLineTotal() != null ? product.getLineTotal() : BigDecimal.ZERO);
        }

        private BigDecimal quantitySold() {
            return quantitySold;
        }

        private BigDecimal salesValue() {
            return salesValue;
        }
    }

    private static class SlowMovingAccumulator {
        private final Item item;
        private final LocalDateTime lastSoldAt;

        private SlowMovingAccumulator(Item item, LocalDateTime lastSoldAt) {
            this.item = item;
            this.lastSoldAt = lastSoldAt;
        }

    }
}
