package com.pos.system.controller;

import com.pos.system.dto.dashboard.MainDashboardResponse;
import com.pos.system.dto.dashboard.MainDashboardResponse.*;
import com.pos.system.service.MainDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/main-dashboard")
@RequiredArgsConstructor
@CrossOrigin
public class MainDashboardController {

    private final MainDashboardService mainDashboardService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/overview")
    public ResponseEntity<MainDashboardResponse> getOverview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) List<Long> branchIds,
            @RequestParam(defaultValue = "REVENUE") String metric) {

        return ResponseEntity.ok(mainDashboardService.getOverview(from, to, branchIds, metric));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/summary")
    public ResponseEntity<SummaryMetrics> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) List<Long> branchIds) {

        return ResponseEntity.ok(mainDashboardService.getSummary(from, to, branchIds));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/sales-performance")
    public ResponseEntity<SalesPerformance> getSalesPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) List<Long> branchIds,
            @RequestParam(defaultValue = "REVENUE") String metric) {

        return ResponseEntity.ok(mainDashboardService.getSalesPerformance(from, to, branchIds, metric));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/branches/performance")
    public ResponseEntity<List<BranchPerformanceEntry>> getBranchPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) List<Long> branchIds) {

        return ResponseEntity.ok(mainDashboardService.getBranchPerformance(from, to, branchIds));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/branches/status")
    public ResponseEntity<BranchStatus> getBranchStatus(
            @RequestParam(required = false) List<Long> branchIds) {

        return ResponseEntity.ok(mainDashboardService.getBranchStatus(branchIds));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/inventory/health")
    public ResponseEntity<InventoryHealth> getInventoryHealth(
            @RequestParam(required = false) List<Long> branchIds) {

        return ResponseEntity.ok(mainDashboardService.getInventoryHealth(branchIds));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/procurement")
    public ResponseEntity<ProcurementMetrics> getProcurement(
            @RequestParam(required = false) List<Long> branchIds) {

        return ResponseEntity.ok(mainDashboardService.getProcurement(branchIds));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/returns")
    public ResponseEntity<ReturnsMetrics> getReturns(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) List<Long> branchIds) {

        return ResponseEntity.ok(mainDashboardService.getReturns(from, to, branchIds));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/operations")
    public ResponseEntity<OperationsMetrics> getOperations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) List<Long> branchIds) {

        return ResponseEntity.ok(mainDashboardService.getOperations(from, to, branchIds));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/promotions")
    public ResponseEntity<PromotionMetrics> getPromotions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) List<Long> branchIds) {

        return ResponseEntity.ok(mainDashboardService.getPromotions(from, to, branchIds));
    }
}
