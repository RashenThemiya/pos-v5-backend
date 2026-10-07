package com.pos.system.service;

import com.pos.system.dto.dashboard.BranchOverviewResponse;
import com.pos.system.dto.dashboard.BranchOverviewResponse.*;
import com.pos.system.model.auth.Branch;
import com.pos.system.model.cash.Counter;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.customer.Customer;
import com.pos.system.model.promotion.Promotion;
import com.pos.system.model.promotion.PromotionRedemption;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.model.sale.OrderProduct;
import com.pos.system.model.sale.Payment;
import com.pos.system.model.sale.SalesReturn;
import com.pos.system.model.stock.StockBatch;
import com.pos.system.model.supplier.PurchaseOrder;
import com.pos.system.model.supplier.Supply;
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
public class BranchOverviewService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    private final BranchRepository branchRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderProductRepository orderProductRepository;
    private final PaymentRepository paymentRepository;
    private final SalesReturnRepository salesReturnRepository;
    private final ItemRepository itemRepository;
    private final StockBatchRepository stockBatchRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CounterRepository counterRepository;
    private final CashSessionRepository cashSessionRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplyRepository supplyRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository promotionRedemptionRepository;

    public BranchOverviewResponse getOverview(Long branchId,
                                              LocalDateTime from,
                                              LocalDateTime to,
                                              String dateRange,
                                              int productLimit,
                                              int lowStockLimit,
                                              int recentLimit,
                                              int attentionLimit) {
        Branch branch = requireBranch(branchId);
        validateDateRange(from, to);

        return BranchOverviewResponse.builder()
                .context(buildContext(branch))
                .filters(DashboardFilters.builder()
                        .dateRange(dateRange)
                        .startDate(from)
                        .endDate(to)
                        .build())
                .summary(getSummary(branchId, from, to))
                .snapshot(getTodaySnapshot(branchId))
                .salesReturnTrend(getSalesReturnPerformance(branchId, from, to))
                .payments(getPaymentMethods(branchId, from, to))
                .products(getTopProducts(branchId, from, to, productLimit))
                .promotions(getPromotionPerformance(branchId, from, to))
                .inventory(getInventoryStatus(branchId, lowStockLimit))
                .attention(getAttention(branchId, attentionLimit))
                .transactions(getRecentTransactions(branchId, recentLimit))
                .build();
    }

    public Summary getSummary(Long branchId, LocalDateTime from, LocalDateTime to) {
        requireBranch(branchId);
        validateDateRange(from, to);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        LocalDateTime yesterdayEnd = todayStart.minusNanos(1);
        LocalDateTime monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime previousMonthStart = monthStart.minusMonths(1);
        LocalDateTime previousMonthEnd = monthStart.minusNanos(1);
        Range previousRange = previousRange(from, to);

        BigDecimal monthlyRevenue = sumOrderTotals(validOrders(branchId, monthStart, now));
        BigDecimal previousMonthlyRevenue = sumOrderTotals(validOrders(branchId, previousMonthStart, previousMonthEnd));
        BigDecimal todaysSales = sumOrderTotals(validOrders(branchId, todayStart, now));
        BigDecimal yesterdaySales = sumOrderTotals(validOrders(branchId, yesterdayStart, yesterdayEnd));
        long totalOrders = validOrders(branchId, from, to).size();
        long previousOrders = validOrders(branchId, previousRange.from(), previousRange.to()).size();
        long activeStaff = userRepository.findByBranchId(branchId).stream()
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .count();

        return Summary.builder()
                .monthlyRevenue(monthlyRevenue)
                .monthlyRevenueChange(changePercent(monthlyRevenue, previousMonthlyRevenue))
                .todaysSales(todaysSales)
                .todaysSalesChange(changePercent(todaysSales, yesterdaySales))
                .totalOrders(totalOrders)
                .totalOrdersChange(changePercent(BigDecimal.valueOf(totalOrders), BigDecimal.valueOf(previousOrders)))
                .activeStaff(activeStaff)
                .build();
    }

    public TodaySnapshot getTodaySnapshot(Long branchId) {
        requireBranch(branchId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.toLocalDate().atStartOfDay();
        List<CustomerOrder> orders = validOrders(branchId, start, now);
        List<SalesReturn> returns = validReturns(branchId, start, now);

        Set<Long> uniqueCustomers = orders.stream()
                .map(CustomerOrder::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        boolean hasWalkInOrders = orders.stream().anyMatch(order -> order.getCustomerId() == null);

        return TodaySnapshot.builder()
                .sales(sumOrderTotals(orders))
                .orders(orders.size())
                .customers(uniqueCustomers.size() + (hasWalkInOrders ? 1 : 0))
                .refunds(sumRefunds(returns))
                .discounts(orders.stream()
                        .map(order -> nvl(order.getDiscount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }

    public SalesReturnPerformance getSalesReturnPerformance(Long branchId, LocalDateTime from, LocalDateTime to) {
        requireBranch(branchId);
        validateDateRange(from, to);
        List<CustomerOrder> orders = validOrders(branchId, from, to);
        List<SalesReturn> returns = validReturns(branchId, from, to);
        List<BucketAccumulator> buckets = createBuckets(from, to);

        for (CustomerOrder order : orders) {
            bucketFor(buckets, order.getOrderDate())
                    .ifPresent(bucket -> bucket.sales = bucket.sales.add(nvl(order.getTotal())));
        }

        for (SalesReturn salesReturn : returns) {
            bucketFor(buckets, salesReturn.getReturnDate())
                    .ifPresent(bucket -> bucket.returns = bucket.returns.add(nvl(salesReturn.getRefundAmount())));
        }

        BigDecimal totalSales = sumOrderTotals(orders);
        BigDecimal totalRefunds = sumRefunds(returns);

        return SalesReturnPerformance.builder()
                .buckets(buckets.stream().map(BucketAccumulator::toResponse).toList())
                .completedOrders(orders.size())
                .averageOrder(orders.isEmpty()
                        ? BigDecimal.ZERO
                        : totalSales.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP))
                .netRevenue(totalSales.subtract(totalRefunds))
                .totalRefunds(totalRefunds)
                .build();
    }

    public PaymentMethods getPaymentMethods(Long branchId, LocalDateTime from, LocalDateTime to) {
        requireBranch(branchId);
        validateDateRange(from, to);
        List<Payment> payments = paymentRepository.findByBranchId(branchId).stream()
                .filter(payment -> isWithin(payment.getPaymentDate(), from, to))
                .toList();

        BigDecimal totalSales = payments.stream()
                .map(payment -> nvl(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PaymentMethodEntry> methods = payments.stream()
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
                            .percentage(percent(amount, totalSales))
                            .transactionCount(entry.getValue().size())
                            .build();
                })
                .sorted(Comparator.comparing(PaymentMethodEntry::getAmount).reversed())
                .toList();

        return PaymentMethods.builder()
                .totalSales(totalSales)
                .methods(methods)
                .build();
    }

    public TopProducts getTopProducts(Long branchId, LocalDateTime from, LocalDateTime to, int limit) {
        requireBranch(branchId);
        validateDateRange(from, to);
        int safeLimit = sanitizeLimit(limit, 10);
        Map<Long, CustomerOrder> ordersById = validOrders(branchId, from, to).stream()
                .collect(Collectors.toMap(CustomerOrder::getOrderId, Function.identity()));

        Map<Long, ProductAccumulator> products = new HashMap<>();
        orderProductRepository.findAll().stream()
                .filter(product -> ordersById.containsKey(product.getOrderId()))
                .forEach(product -> products
                        .computeIfAbsent(product.getItemId(), ProductAccumulator::new)
                        .add(product));

        Map<Long, Item> itemsById = itemRepository.findByBranchId(branchId).stream()
                .collect(Collectors.toMap(Item::getItemId, Function.identity(), (a, b) -> a));

        List<TopProductEntry> ranked = products.values().stream()
                .sorted(Comparator.comparing(ProductAccumulator::salesValue).reversed())
                .limit(safeLimit)
                .map(accumulator -> {
                    Item item = itemsById.get(accumulator.itemId);
                    return TopProductEntry.builder()
                            .productId(accumulator.itemId)
                            .productName(item != null ? item.getName() : "Product #" + accumulator.itemId)
                            .sku(item != null ? item.getSku() : null)
                            .quantity(accumulator.quantity)
                            .salesValue(accumulator.salesValue)
                            .build();
                })
                .toList();

        List<TopProductEntry> withRanks = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            TopProductEntry item = ranked.get(i);
            item.setRank(i + 1);
            withRanks.add(item);
        }

        return TopProducts.builder().items(withRanks).build();
    }

    public PromotionPerformance getPromotionPerformance(Long branchId, LocalDateTime from, LocalDateTime to) {
        requireBranch(branchId);
        validateDateRange(from, to);
        LocalDateTime now = LocalDateTime.now();
        List<Promotion> promotions = promotionRepository.findByBranchId(branchId);
        Map<Long, Promotion> promotionsById = promotions.stream()
                .collect(Collectors.toMap(Promotion::getPromotionId, Function.identity(), (a, b) -> a));
        Map<Long, CustomerOrder> ordersById = orderRepository.findByBranchIdOrderByOrderDateDesc(branchId).stream()
                .collect(Collectors.toMap(CustomerOrder::getOrderId, Function.identity(), (a, b) -> a));

        List<PromotionRedemption> redemptions = promotionRedemptionRepository.findAll().stream()
                .filter(redemption -> branchId.equals(redemption.getBranchId()))
                .filter(redemption -> {
                    CustomerOrder order = ordersById.get(redemption.getOrderId());
                    LocalDateTime usedAt = redemption.getUsedAt() != null
                            ? redemption.getUsedAt()
                            : order != null ? order.getOrderDate() : null;
                    return isWithin(usedAt, from, to) && order != null && isValidOrder(order);
                })
                .toList();

        BigDecimal totalDiscount = redemptions.stream()
                .map(redemption -> nvl(redemption.getDiscountAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, PromotionAccumulator> performers = new HashMap<>();
        for (PromotionRedemption redemption : redemptions) {
            performers
                    .computeIfAbsent(redemption.getPromotionId(), PromotionAccumulator::new)
                    .add(redemption, ordersById.get(redemption.getOrderId()));
        }

        List<PromotionPerformer> topPerformers = performers.values().stream()
                .sorted(Comparator.comparing(PromotionAccumulator::promotionSales).reversed())
                .limit(5)
                .map(accumulator -> {
                    Promotion promotion = promotionsById.get(accumulator.promotionId);
                    return PromotionPerformer.builder()
                            .promotionId(accumulator.promotionId)
                            .promotionName(promotion != null ? promotion.getName() : "Promotion #" + accumulator.promotionId)
                            .promoCode(promotion != null ? promotion.getPromoCode() : null)
                            .promotionSales(accumulator.promotionSales)
                            .discountValue(accumulator.discountValue)
                            .usageCount(accumulator.usageCount)
                            .build();
                })
                .toList();

        return PromotionPerformance.builder()
                .activeCount(promotions.stream().filter(promotion -> isActivePromotion(promotion, now)).count())
                .usageCount(redemptions.size())
                .totalDiscountValue(totalDiscount)
                .averageDiscount(redemptions.isEmpty()
                        ? BigDecimal.ZERO
                        : totalDiscount.divide(BigDecimal.valueOf(redemptions.size()), 2, RoundingMode.HALF_UP))
                .topPerformers(topPerformers)
                .alerts(buildPromotionAlerts(promotions, now))
                .build();
    }

    public InventoryStatus getInventoryStatus(Long branchId, int lowStockLimit) {
        requireBranch(branchId);
        int safeLimit = sanitizeLimit(lowStockLimit, 10);
        List<Item> items = itemRepository.findByBranchId(branchId).stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .toList();
        Map<Long, BigDecimal> quantityByItem = quantityByItem(branchId);

        List<LowStockItem> lowStockItems = items.stream()
                .map(item -> toLowStockItem(item, quantityByItem.getOrDefault(item.getItemId(), BigDecimal.ZERO)))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(LowStockItem::getCurrentQuantity))
                .toList();

        long outOfStock = items.stream()
                .filter(item -> quantityByItem.getOrDefault(item.getItemId(), BigDecimal.ZERO)
                        .compareTo(BigDecimal.ZERO) <= 0)
                .count();
        long lowStock = lowStockItems.size();
        long inStock = Math.max(items.size() - outOfStock - lowStock, 0);

        return InventoryStatus.builder()
                .totalProducts(items.size())
                .inStock(inStock)
                .lowStock(lowStock)
                .outOfStock(outOfStock)
                .inStockPercent(percent(BigDecimal.valueOf(inStock), BigDecimal.valueOf(items.size())))
                .lowStockPercent(percent(BigDecimal.valueOf(lowStock), BigDecimal.valueOf(items.size())))
                .outOfStockPercent(percent(BigDecimal.valueOf(outOfStock), BigDecimal.valueOf(items.size())))
                .lowStockItems(lowStockItems.stream().limit(safeLimit).toList())
                .build();
    }

    public AttentionPanel getAttention(Long branchId, int limit) {
        requireBranch(branchId);
        int safeLimit = sanitizeLimit(limit, 10);
        List<AttentionAlert> alerts = new ArrayList<>();

        InventoryStatus inventory = getInventoryStatus(branchId, 1);
        if (inventory.getOutOfStock() > 0) {
            alerts.add(attention("out-of-stock", "OUT_OF_STOCK", "CRITICAL",
                    inventory.getOutOfStock() + " products are out of stock",
                    "Review unavailable products before serving customers.",
                    "/branch/" + branchId + "/stocks", null));
        }
        if (inventory.getLowStock() > 0) {
            alerts.add(attention("low-stock", "LOW_STOCK", "WARNING",
                    inventory.getLowStock() + " products are below threshold",
                    "Create replenishment actions for low stock products.",
                    "/branch/" + branchId + "/stocks", null));
        }

        List<PurchaseOrder> openPurchaseOrders = purchaseOrderRepository.findByBranchId(branchId).stream()
                .filter(this::isOpenPurchaseOrder)
                .toList();
        long overduePurchaseOrders = openPurchaseOrders.stream()
                .filter(po -> po.getExpectedDate() != null && po.getExpectedDate().isBefore(LocalDate.now()))
                .count();
        if (overduePurchaseOrders > 0) {
            alerts.add(attention("overdue-pos", "OVERDUE_PURCHASE_ORDER", "CRITICAL",
                    overduePurchaseOrders + " purchase orders are overdue",
                    "Follow up expected receiving dates and supplier status.",
                    "/branch/" + branchId + "/purchase-orders", null));
        } else if (!openPurchaseOrders.isEmpty()) {
            alerts.add(attention("open-pos", "OPEN_PURCHASE_ORDER", "INFO",
                    openPurchaseOrders.size() + " purchase orders are open",
                    "Track receiving progress for pending procurement.",
                    "/branch/" + branchId + "/purchase-orders", null));
        }

        List<Supply> unpaidSupplies = supplyRepository
                .findByBranchIdAndPaymentStatusNotOrderBySupplyDateDesc(branchId, "PAID");
        BigDecimal supplierBalance = unpaidSupplies.stream()
                .map(supply -> nvl(supply.getBalanceAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (supplierBalance.compareTo(BigDecimal.ZERO) > 0) {
            alerts.add(attention("supplier-payments", "SUPPLIER_PAYMENT_DUE", "WARNING",
                    "Supplier balance is pending",
                    "Outstanding supplier balance is Rs. " + supplierBalance + ".",
                    "/branch/" + branchId + "/supplier-payments", null));
        }

        long openSessions = openSessionCount(branchId);
        if (openSessions > 0) {
            alerts.add(attention("open-sessions", "OPEN_CASH_SESSION", "INFO",
                    openSessions + " cash sessions are open",
                    "Review open registers before end-of-day reconciliation.",
                    "/branch/" + branchId + "/counters", null));
        }

        getPromotionPerformance(branchId, LocalDateTime.now().minusDays(30), LocalDateTime.now())
                .getAlerts()
                .stream()
                .limit(2)
                .forEach(alert -> alerts.add(attention(alert.getId(), alert.getType(), alert.getSeverity(),
                        alert.getTitle(), alert.getDescription(), alert.getTargetRoute(), alert.getPromotionId())));

        return AttentionPanel.builder()
                .alerts(alerts.stream().limit(safeLimit).toList())
                .build();
    }

    public RecentTransactions getRecentTransactions(Long branchId, int limit) {
        requireBranch(branchId);
        int safeLimit = sanitizeLimit(limit, 10);
        Map<Long, Customer> customersById = customerRepository.findByBranchId(branchId).stream()
                .collect(Collectors.toMap(Customer::getCustomerId, Function.identity(), (a, b) -> a));
        Map<Long, List<Payment>> paymentsByOrder = paymentRepository.findByBranchId(branchId).stream()
                .filter(payment -> payment.getOrderId() != null)
                .collect(Collectors.groupingBy(Payment::getOrderId));

        List<RecentTransaction> transactions = orderRepository.findByBranchIdOrderByOrderDateDesc(branchId).stream()
                .limit(safeLimit)
                .map(order -> {
                    Customer customer = customersById.get(order.getCustomerId());
                    return RecentTransaction.builder()
                            .orderId(order.getOrderId())
                            .invoice(order.getInvoiceNo())
                            .customer(customer != null ? customer.getName() : "Walk-in Customer")
                            .amount(nvl(order.getTotal()))
                            .method(primaryPaymentMethod(paymentsByOrder.getOrDefault(order.getOrderId(), List.of())))
                            .time(order.getOrderDate())
                            .status(order.getStatus())
                            .build();
                })
                .toList();

        return RecentTransactions.builder().items(transactions).build();
    }

    private BranchContext buildContext(Branch branch) {
        return BranchContext.builder()
                .branchId(branch.getBranchId())
                .branchName(branch.getName())
                .branchCode(null)
                .timezone(branch.getTimezone())
                .address(branch.getAddress())
                .phone(branch.getPhone())
                .active(branch.getIsActive())
                .build();
    }

    private Branch requireBranch(Long branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found: " + branchId));
    }

    private List<CustomerOrder> validOrders(Long branchId, LocalDateTime from, LocalDateTime to) {
        return orderRepository.findByBranchIdAndOrderDateBetweenOrderByOrderDateDesc(branchId, from, to).stream()
                .filter(this::isValidOrder)
                .toList();
    }

    private List<SalesReturn> validReturns(Long branchId, LocalDateTime from, LocalDateTime to) {
        return salesReturnRepository.findByBranchIdOrderByReturnDateDesc(branchId).stream()
                .filter(salesReturn -> isWithin(salesReturn.getReturnDate(), from, to))
                .filter(this::isValidReturn)
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
        return !isCompletedPurchaseOrder(purchaseOrder) && !isCancelledPurchaseOrder(purchaseOrder);
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

    private List<PromotionAlert> buildPromotionAlerts(List<Promotion> promotions, LocalDateTime now) {
        List<PromotionAlert> alerts = new ArrayList<>();
        for (Promotion promotion : promotions) {
            if (!isActivePromotion(promotion, now)) {
                continue;
            }

            long usageCount = promotionRedemptionRepository.countByPromotionId(promotion.getPromotionId());
            if (promotion.getEndAt() != null
                    && !promotion.getEndAt().isBefore(now)
                    && !promotion.getEndAt().isAfter(now.plusDays(7))) {
                alerts.add(promotionAlert(promotion, "PROMOTION_EXPIRING", "WARNING",
                        "Promotion is expiring soon",
                        promotion.getName() + " ends on " + promotion.getEndAt() + "."));
            }

            if (promotion.getMaxUsesTotal() != null && usageCount >= promotion.getMaxUsesTotal()) {
                alerts.add(promotionAlert(promotion, "PROMOTION_LIMIT_REACHED", "CRITICAL",
                        "Promotion usage limit reached",
                        promotion.getName() + " has reached its usage limit."));
            } else if (usageCount == 0) {
                alerts.add(promotionAlert(promotion, "PROMOTION_ZERO_REDEMPTIONS", "INFO",
                        "Promotion has no redemptions",
                        promotion.getName() + " has not been used yet."));
            }
        }
        return alerts.stream().limit(10).toList();
    }

    private PromotionAlert promotionAlert(Promotion promotion,
                                          String type,
                                          String severity,
                                          String title,
                                          String description) {
        return PromotionAlert.builder()
                .id(type.toLowerCase(Locale.ROOT) + "-" + promotion.getPromotionId())
                .type(type)
                .severity(severity)
                .title(title)
                .description(description)
                .promotionId(promotion.getPromotionId())
                .targetRoute("/branch/" + promotion.getBranchId() + "/promotions")
                .build();
    }

    private AttentionAlert attention(String id,
                                     String type,
                                     String severity,
                                     String title,
                                     String description,
                                     String targetRoute,
                                     Long targetId) {
        return AttentionAlert.builder()
                .id(id)
                .type(type)
                .severity(severity)
                .title(title)
                .description(description)
                .targetRoute(targetRoute)
                .targetId(targetId)
                .build();
    }

    private LowStockItem toLowStockItem(Item item, BigDecimal currentQuantity) {
        BigDecimal threshold = item.getMinStock();
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (currentQuantity.compareTo(BigDecimal.ZERO) <= 0 || currentQuantity.compareTo(threshold) > 0) {
            return null;
        }

        return LowStockItem.builder()
                .productId(item.getItemId())
                .productName(item.getName())
                .sku(item.getSku())
                .currentQuantity(currentQuantity)
                .threshold(threshold)
                .build();
    }

    private Map<Long, BigDecimal> quantityByItem(Long branchId) {
        return stockBatchRepository.findByBranchId(branchId).stream()
                .collect(Collectors.groupingBy(
                        StockBatch::getItemId,
                        Collectors.reducing(BigDecimal.ZERO, this::availableQty, BigDecimal::add)));
    }

    private BigDecimal availableQty(StockBatch batch) {
        return batch.getAvailableQty() != null ? batch.getAvailableQty() : nvl(batch.getQtyRemaining());
    }

    private long openSessionCount(Long branchId) {
        Set<Long> counterIds = counterRepository.findByBranchId(branchId).stream()
                .map(Counter::getCounterId)
                .collect(Collectors.toSet());

        return cashSessionRepository.findAll().stream()
                .filter(session -> counterIds.contains(session.getCounterId()))
                .filter(session -> "OPEN".equalsIgnoreCase(safe(session.getStatus())))
                .count();
    }

    private String primaryPaymentMethod(List<Payment> payments) {
        if (payments.isEmpty()) {
            return "UNPAID";
        }
        return payments.stream()
                .max(Comparator.comparing(payment -> nvl(payment.getAmount())))
                .map(payment -> normalizePaymentMethod(payment.getPaymentMethod()))
                .orElse("UNKNOWN");
    }

    private String normalizePaymentMethod(String method) {
        if (method == null || method.isBlank()) {
            return "UNKNOWN";
        }
        return method.trim().replace('_', ' ').toUpperCase(Locale.ROOT);
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
            buckets.add(new BucketAccumulator(date.toString(), DAY_LABEL.format(date), bucketFrom, bucketTo, index++));
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
            buckets.add(new BucketAccumulator("week-" + index, "Week " + index, cursor, bucketTo, index));
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
            LocalDateTime nextMonth = cursor.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay();
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

    private Optional<BucketAccumulator> bucketFor(List<BucketAccumulator> buckets, LocalDateTime value) {
        return buckets.stream().filter(bucket -> isWithin(value, bucket.from, bucket.to)).findFirst();
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

    private int sanitizeLimit(int limit, int fallback) {
        int value = limit > 0 ? limit : fallback;
        return Math.min(value, 50);
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
        private BigDecimal sales = BigDecimal.ZERO;
        private BigDecimal returns = BigDecimal.ZERO;

        private BucketAccumulator(String key, String label, LocalDateTime from, LocalDateTime to, int index) {
            this.key = key;
            this.label = label;
            this.from = from;
            this.to = to;
            this.index = index;
        }

        private SalesReturnBucket toResponse() {
            return SalesReturnBucket.builder()
                    .bucketKey(key != null ? key : "bucket-" + index)
                    .label(label)
                    .bucketFrom(from)
                    .bucketTo(to)
                    .sales(sales)
                    .returns(returns)
                    .build();
        }
    }

    private static class ProductAccumulator {
        private final Long itemId;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal salesValue = BigDecimal.ZERO;

        private ProductAccumulator(Long itemId) {
            this.itemId = itemId;
        }

        private void add(OrderProduct product) {
            quantity = quantity.add(product.getQuantity() != null ? product.getQuantity() : BigDecimal.ZERO);
            salesValue = salesValue.add(product.getLineTotal() != null ? product.getLineTotal() : BigDecimal.ZERO);
        }

        private BigDecimal salesValue() {
            return salesValue;
        }
    }

    private static class PromotionAccumulator {
        private final Long promotionId;
        private final Set<Long> orderIds = new HashSet<>();
        private BigDecimal promotionSales = BigDecimal.ZERO;
        private BigDecimal discountValue = BigDecimal.ZERO;
        private long usageCount;

        private PromotionAccumulator(Long promotionId) {
            this.promotionId = promotionId;
        }

        private void add(PromotionRedemption redemption, CustomerOrder order) {
            usageCount++;
            discountValue = discountValue.add(redemption.getDiscountAmount() != null
                    ? redemption.getDiscountAmount()
                    : BigDecimal.ZERO);

            if (order != null && orderIds.add(order.getOrderId())) {
                promotionSales = promotionSales.add(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
            }
        }

        private BigDecimal promotionSales() {
            return promotionSales;
        }
    }
}
