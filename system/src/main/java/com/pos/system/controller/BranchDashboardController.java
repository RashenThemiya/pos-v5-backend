package com.pos.system.controller;

import com.pos.system.dto.dashboard.BranchDashboardResponse;
import com.pos.system.service.BranchDashboardService;
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
}
