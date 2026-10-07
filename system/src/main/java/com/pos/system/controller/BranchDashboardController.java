package com.pos.system.controller;

import com.pos.system.dto.dashboard.BranchDashboardResponse;
import com.pos.system.dto.dashboard.BranchOverviewResponse;
import com.pos.system.dto.dashboard.BranchOverviewResponse.*;
import com.pos.system.service.BranchDashboardService;
import com.pos.system.service.BranchOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Branch Dashboard Controller
 *
 * Provides owner-facing summary endpoints for a specific branch.
 *
 * Base path: /api/dashboard/branches
 *
 * Endpoints
 * ─────────
 * GET /api/dashboard/branches/{branchId}                       — custom date range
 * GET /api/dashboard/branches/{branchId}/today                 — today only
 * GET /api/dashboard/branches/{branchId}/this-month            — current month to now
 */
@RestController
@RequestMapping("/api/dashboard/branches")
@RequiredArgsConstructor
@CrossOrigin
public class BranchDashboardController {

    private final BranchDashboardService branchDashboardService;
    private final BranchOverviewService branchOverviewService;

    /**
     * Full branch dashboard over an arbitrary date range.
     *
     * @param branchId target branch
     * @param from     range start  (ISO-8601, e.g. 2025-01-01T00:00:00)
     * @param to       range end    (ISO-8601, e.g. 2025-01-31T23:59:59)
     */
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}")
    public ResponseEntity<BranchDashboardResponse> getDashboard(
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ResponseEntity.ok(branchDashboardService.getDashboard(branchId, from, to));
    }

    /**
     * Branch dashboard for today (midnight → current time).
     */
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}/today")
    public ResponseEntity<BranchDashboardResponse> getTodayDashboard(
            @PathVariable Long branchId) {

        return ResponseEntity.ok(branchDashboardService.getTodayDashboard(branchId));
    }

    /**
     * Branch dashboard for the current calendar month (1st of month → now).
     */
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}/this-month")
    public ResponseEntity<BranchDashboardResponse> getMonthDashboard(
            @PathVariable Long branchId) {

        return ResponseEntity.ok(branchDashboardService.getMonthDashboard(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}/overview")
    public ResponseEntity<BranchOverviewResponse> getBranchOverview(
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange,
            @RequestParam(defaultValue = "5") int productLimit,
            @RequestParam(defaultValue = "10") int lowStockLimit,
            @RequestParam(defaultValue = "10") int recentLimit,
            @RequestParam(defaultValue = "10") int attentionLimit) {

        return ResponseEntity.ok(branchOverviewService.getOverview(
                branchId, from, to, dateRange, productLimit, lowStockLimit, recentLimit, attentionLimit));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}/overview/summary")
    public ResponseEntity<Summary> getBranchOverviewSummary(
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ResponseEntity.ok(branchOverviewService.getSummary(branchId, from, to));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}/overview/today-snapshot")
    public ResponseEntity<TodaySnapshot> getTodaySnapshot(@PathVariable Long branchId) {
        return ResponseEntity.ok(branchOverviewService.getTodaySnapshot(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}/overview/sales-return-performance")
    public ResponseEntity<SalesReturnPerformance> getSalesReturnPerformance(
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ResponseEntity.ok(branchOverviewService.getSalesReturnPerformance(branchId, from, to));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}/overview/payment-methods")
    public ResponseEntity<PaymentMethods> getPaymentMethods(
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ResponseEntity.ok(branchOverviewService.getPaymentMethods(branchId, from, to));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}/overview/top-products")
    public ResponseEntity<TopProducts> getTopProducts(
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "5") int limit) {

        return ResponseEntity.ok(branchOverviewService.getTopProducts(branchId, from, to, limit));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}/overview/promotions")
    public ResponseEntity<PromotionPerformance> getPromotionPerformance(
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ResponseEntity.ok(branchOverviewService.getPromotionPerformance(branchId, from, to));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}/overview/inventory")
    public ResponseEntity<InventoryStatus> getInventoryStatus(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "10") int lowStockLimit) {

        return ResponseEntity.ok(branchOverviewService.getInventoryStatus(branchId, lowStockLimit));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}/overview/attention")
    public ResponseEntity<AttentionPanel> getAttention(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(branchOverviewService.getAttention(branchId, limit));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/{branchId}/overview/recent-transactions")
    public ResponseEntity<RecentTransactions> getRecentTransactions(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(branchOverviewService.getRecentTransactions(branchId, limit));
    }
}
