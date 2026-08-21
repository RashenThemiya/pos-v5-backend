package com.pos.system.service;

import com.pos.system.dto.dashboard.BranchDashboardResponse;
import com.pos.system.dto.dashboard.BranchDashboardResponse.*;
import com.pos.system.model.auth.Branch;
import com.pos.system.model.cash.CashSession;
import com.pos.system.model.cash.Expense;
import com.pos.system.model.customer.Customer;
import com.pos.system.model.sale.Payment;
import com.pos.system.model.sale.SalesReturn;
import com.pos.system.model.stock.Stock;
import com.pos.system.model.stock.StockBatch;
import com.pos.system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchDashboardService {

    private final BranchRepository            branchRepository;
    private final CustomerOrderRepository     orderRepository;
    private final PaymentRepository           paymentRepository;
    private final ExpenseRepository           expenseRepository;
    private final SalesReturnRepository       salesReturnRepository;
    private final StockRepository             stockRepository;
    private final StockBatchRepository        stockBatchRepository;
    private final CustomerRepository          customerRepository;
    private final CounterRepository           counterRepository;
    private final CashSessionRepository       cashSessionRepository;

    private static final DateTimeFormatter SALES_DAY_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d");

    // ─── Public API ───────────────────────────────────────────────────────

    /**
     * Full dashboard for a branch over an explicit date range.
     */
    public BranchDashboardResponse getDashboard(Long branchId,
                                                LocalDateTime from,
                                                LocalDateTime to) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found: " + branchId));

        return BranchDashboardResponse.builder()
                .branchId(branch.getBranchId())
                .branchName(branch.getName())
                .branchAddress(branch.getAddress())
                .branchPhone(branch.getPhone())
                .isActive(branch.getIsActive())
                .periodFrom(from)
                .periodTo(to)
                .sales(buildSalesSummary(branchId, from, to))
                .returns(buildReturnsSummary(branchId, from, to))
                .expenses(buildExpenseSummary(branchId, from, to))
                .stock(buildStockSummary(branchId))
                .customers(buildCustomerSummary(branchId, from, to))
                .counters(buildCounterSummary(branchId))
                .topItems(buildTopItems(branchId, from, to, 10))
                .paymentBreakdown(buildPaymentBreakdown(branchId, from, to))
                .dailySales(buildDailySales(branchId, from, to))
                .build();
    }

    /**
     * Today's dashboard (midnight → now).
     */
    public BranchDashboardResponse getTodayDashboard(Long branchId) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime now        = LocalDateTime.now();
        return getDashboard(branchId, startOfDay, now);
    }

    /**
     * This-month dashboard (1st of month → now).
     */
    public BranchDashboardResponse getMonthDashboard(Long branchId) {
        LocalDateTime startOfMonth = LocalDateTime.now()
                .withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        return getDashboard(branchId, startOfMonth, now);
    }

    // ─── Private builders ─────────────────────────────────────────────────

    private SalesSummary buildSalesSummary(Long branchId,
                                           LocalDateTime from,
                                           LocalDateTime to) {

        long totalOrders     = orderRepository.countByBranchAndPeriod(branchId, from, to);
        long completedOrders = orderRepository.countByBranchAndStatusAndPeriod(branchId, "COMPLETED", from, to);
        long cancelledOrders = orderRepository.countByBranchAndStatusAndPeriod(branchId, "CANCELLED", from, to);
        long pendingOrders   = totalOrders - completedOrders - cancelledOrders;

        BigDecimal grossRevenue  = orderRepository.sumGrossRevenue(branchId, from, to);
        BigDecimal totalDiscount = orderRepository.sumDiscount(branchId, from, to);
        BigDecimal totalTax      = orderRepository.sumTax(branchId, from, to);

        // Net revenue = gross - refunds from sales returns in the period
        BigDecimal totalRefunds = salesReturnRepository
                .findByBranchIdOrderByReturnDateDesc(branchId).stream()
                .filter(r -> r.getReturnDate() != null
                          && !r.getReturnDate().isBefore(from)
                          && !r.getReturnDate().isAfter(to))
                .map(SalesReturn::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netRevenue = grossRevenue.subtract(totalRefunds);

        BigDecimal avgOrderValue = completedOrders > 0
                ? grossRevenue.divide(BigDecimal.valueOf(completedOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return SalesSummary.builder()
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .pendingOrders(Math.max(pendingOrders, 0))
                .grossRevenue(grossRevenue)
                .totalDiscount(totalDiscount)
                .totalTax(totalTax)
                .netRevenue(netRevenue)
                .averageOrderValue(avgOrderValue)
                .build();
    }

    private ReturnsSummary buildReturnsSummary(Long branchId,
                                               LocalDateTime from,
                                               LocalDateTime to) {
        List<SalesReturn> returns = salesReturnRepository
                .findByBranchIdOrderByReturnDateDesc(branchId).stream()
                .filter(r -> r.getReturnDate() != null
                          && !r.getReturnDate().isBefore(from)
                          && !r.getReturnDate().isAfter(to))
                .collect(Collectors.toList());

        BigDecimal totalRefund = returns.stream()
                .map(r -> r.getRefundAmount() != null ? r.getRefundAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ReturnsSummary.builder()
                .totalReturns(returns.size())
                .totalRefundAmount(totalRefund)
                .build();
    }

    private ExpenseSummary buildExpenseSummary(Long branchId,
                                               LocalDateTime from,
                                               LocalDateTime to) {
        List<Expense> expenses = expenseRepository
                .findByBranchIdOrderByExpenseDateDesc(branchId).stream()
                .filter(e -> e.getExpenseDate() != null
                          && !e.getExpenseDate().isBefore(from)
                          && !e.getExpenseDate().isAfter(to))
                .collect(Collectors.toList());

        BigDecimal totalAmount = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Group by category
        Map<String, List<Expense>> byCategory = expenses.stream()
                .collect(Collectors.groupingBy(Expense::getCategory));

        List<ExpenseCategoryEntry> categoryEntries = byCategory.entrySet().stream()
                .map(entry -> ExpenseCategoryEntry.builder()
                        .category(entry.getKey())
                        .count(entry.getValue().size())
                        .amount(entry.getValue().stream()
                                .map(Expense::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .collect(Collectors.toList());

        return ExpenseSummary.builder()
                .totalExpenses(expenses.size())
                .totalExpenseAmount(totalAmount)
                .byCategory(categoryEntries)
                .build();
    }

    private StockSummary buildStockSummary(Long branchId) {
        List<Stock> stocks = stockRepository.findByBranchId(branchId);
        long totalSkus = stocks.size();

        // Derive quantities from StockBatch: sum remaining quantity per item
        List<StockBatch> batches = stockBatchRepository.findByBranchId(branchId);

        Map<Long, BigDecimal> qtyByItem = batches.stream()
                .filter(b -> b.getQtyRemaining() != null)
                .collect(Collectors.groupingBy(
                        StockBatch::getItemId,
                        Collectors.reducing(BigDecimal.ZERO,
                                StockBatch::getQtyRemaining,
                                BigDecimal::add)));

        long outOfStockSkus = qtyByItem.values().stream()
                .filter(q -> q.compareTo(BigDecimal.ZERO) <= 0)
                .count();

        // Items in stock table but with no batch at all also count as out-of-stock
        long itemsWithNoBatch = stocks.stream()
                .filter(s -> !qtyByItem.containsKey(s.getItemId()))
                .count();

        // Low stock: quantity > 0 but <= 5 (simple heuristic; extend with reorder level later)
        long lowStockSkus = qtyByItem.values().stream()
                .filter(q -> q.compareTo(BigDecimal.ZERO) > 0
                          && q.compareTo(BigDecimal.valueOf(5)) <= 0)
                .count();

        return StockSummary.builder()
                .totalSkus(totalSkus)
                .lowStockSkus(lowStockSkus)
                .outOfStockSkus(outOfStockSkus + itemsWithNoBatch)
                .build();
    }

    private CustomerSummary buildCustomerSummary(Long branchId,
                                                 LocalDateTime from,
                                                 LocalDateTime to) {
        long totalCustomers = customerRepository.findByBranchId(branchId).size();

        long newCustomers = customerRepository.findByBranchId(branchId).stream()
                .filter(c -> c.getCreatedAt() != null
                          && !c.getCreatedAt().isBefore(from)
                          && !c.getCreatedAt().isAfter(to))
                .count();

        long activeCustomers = orderRepository.countActiveCustomers(branchId, from, to);

        return CustomerSummary.builder()
                .totalCustomers(totalCustomers)
                .newCustomers(newCustomers)
                .activeCustomers(activeCustomers)
                .build();
    }

    private CounterSummary buildCounterSummary(Long branchId) {
        long totalCounters  = counterRepository.findByBranchId(branchId).size();
        long activeCounters = counterRepository.findByBranchIdAndIsActiveTrue(branchId).size();

        long openSessions = counterRepository.findByBranchIdAndIsActiveTrue(branchId).stream()
                .filter(counter -> cashSessionRepository
                        .findByCounterIdAndStatus(counter.getCounterId(), "OPEN")
                        .isPresent())
                .count();

        return CounterSummary.builder()
                .totalCounters(totalCounters)
                .activeCounters(activeCounters)
                .openSessions(openSessions)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<TopItemEntry> buildTopItems(Long branchId,
                                             LocalDateTime from,
                                             LocalDateTime to,
                                             int limit) {
        List<Object[]> rows = orderRepository.findTopItemsByBranchAndPeriod(
                branchId, from, to, PageRequest.of(0, limit));

        return rows.stream().map(row -> TopItemEntry.builder()
                .itemId(((Number) row[0]).longValue())
                .itemName((String) row[1])
                .sku((String) row[2])
                .quantitySold(((Number) row[3]).longValue())
                .revenue(row[4] instanceof BigDecimal
                        ? (BigDecimal) row[4]
                        : BigDecimal.valueOf(((Number) row[4]).doubleValue()))
                .build())
                .collect(Collectors.toList());
    }

    private List<PaymentMethodEntry> buildPaymentBreakdown(Long branchId,
                                                            LocalDateTime from,
                                                            LocalDateTime to) {
        List<Payment> payments = paymentRepository.findByBranchId(branchId).stream()
                .filter(p -> p.getPaymentDate() != null
                          && !p.getPaymentDate().isBefore(from)
                          && !p.getPaymentDate().isAfter(to))
                .collect(Collectors.toList());

        Map<String, List<Payment>> byMethod = payments.stream()
                .collect(Collectors.groupingBy(p ->
                        p.getPaymentMethod() != null ? p.getPaymentMethod() : "UNKNOWN"));

        return byMethod.entrySet().stream()
                .map(entry -> PaymentMethodEntry.builder()
                        .paymentMethod(entry.getKey())
                        .transactionCount(entry.getValue().size())
                        .totalAmount(entry.getValue().stream()
                                .map(Payment::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .sorted((a, b) -> b.getTotalAmount().compareTo(a.getTotalAmount()))
                .collect(Collectors.toList());
    }

    private List<DailySalesEntry> buildDailySales(Long branchId,
                                                  LocalDateTime from,
                                                  LocalDateTime to) {
        LocalDate startDate = from.toLocalDate();
        LocalDate endDate = to.toLocalDate();

        Map<LocalDate, DailySalesEntry> byDate = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            byDate.put(date, DailySalesEntry.builder()
                    .date(date)
                    .label(SALES_DAY_LABEL_FORMATTER.format(date))
                    .orderCount(0)
                    .grossRevenue(BigDecimal.ZERO)
                    .build());
        }

        orderRepository.findDailySalesByBranchAndPeriod(branchId, from, to)
                .forEach(row -> {
                    LocalDate date = toLocalDate(row[0]);
                    if (date == null) {
                        return;
                    }

                    byDate.put(date, DailySalesEntry.builder()
                            .date(date)
                            .label(SALES_DAY_LABEL_FORMATTER.format(date))
                            .orderCount(((Number) row[1]).longValue())
                            .grossRevenue(row[2] instanceof BigDecimal
                                    ? (BigDecimal) row[2]
                                    : BigDecimal.valueOf(((Number) row[2]).doubleValue()))
                            .build());
                });

        return List.copyOf(byDate.values());
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }

        if (value instanceof Date date) {
            return date.toLocalDate();
        }

        if (value instanceof java.util.Date date) {
            return new Date(date.getTime()).toLocalDate();
        }

        if (value instanceof String text) {
            return LocalDate.parse(text);
        }

        return null;
    }
}
