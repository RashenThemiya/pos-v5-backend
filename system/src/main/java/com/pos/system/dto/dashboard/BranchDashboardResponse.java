package com.pos.system.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDashboardResponse {

    // ── Branch Identity ──────────────────────────────────────────────────
    private Long branchId;
    private String branchName;
    private String branchAddress;
    private String branchPhone;
    private Boolean isActive;

    // ── Period ───────────────────────────────────────────────────────────
    private LocalDateTime periodFrom;
    private LocalDateTime periodTo;

    // ── Sales Summary ────────────────────────────────────────────────────
    private SalesSummary sales;

    // ── Returns Summary ─────────────────────────────────────────────────
    private ReturnsSummary returns;

    // ── Expense Summary ──────────────────────────────────────────────────
    private ExpenseSummary expenses;

    // ── Stock Summary ────────────────────────────────────────────────────
    private StockSummary stock;

    // ── Customer Summary ─────────────────────────────────────────────────
    private CustomerSummary customers;

    // ── Counter / Session Summary ────────────────────────────────────────
    private CounterSummary counters;

    // ── Top-selling Items ─────────────────────────────────────────────────
    private List<TopItemEntry> topItems;

    // ── Payment Method Breakdown ──────────────────────────────────────────
    private List<PaymentMethodEntry> paymentBreakdown;

    private List<DailySalesEntry> dailySales;

    // ─────────────────────────────────────────────────────────────────────
    // Nested summary classes
    // ─────────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesSummary {
        private long totalOrders;
        private long completedOrders;
        private long cancelledOrders;
        private long pendingOrders;
        private BigDecimal grossRevenue;       // sum of order totals (completed)
        private BigDecimal totalDiscount;
        private BigDecimal totalTax;
        private BigDecimal netRevenue;         // grossRevenue - returns refund
        private BigDecimal averageOrderValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnsSummary {
        private long totalReturns;
        private BigDecimal totalRefundAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseSummary {
        private long totalExpenses;
        private BigDecimal totalExpenseAmount;
        private List<ExpenseCategoryEntry> byCategory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseCategoryEntry {
        private String category;
        private BigDecimal amount;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockSummary {
        private long totalSkus;          // distinct items with stock records
        private long lowStockSkus;       // items whose quantity is below reorder threshold
        private long outOfStockSkus;     // items with zero or negative quantity
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerSummary {
        private long totalCustomers;
        private long newCustomers;       // registered within the period
        private long activeCustomers;    // placed at least one order in the period
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CounterSummary {
        private long totalCounters;
        private long activeCounters;
        private long openSessions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopItemEntry {
        private Long itemId;
        private String itemName;
        private String sku;
        private long quantitySold;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodEntry {
        private String paymentMethod;
        private long transactionCount;
        private BigDecimal totalAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailySalesEntry {
        private LocalDate date;
        private String label;
        private long orderCount;
        private BigDecimal grossRevenue;
    }
}
