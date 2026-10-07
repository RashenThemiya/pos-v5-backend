package com.pos.system.controller;

import com.pos.system.dto.stock.*;
import com.pos.system.dto.stock.StockAnalyticsResponse.*;
import com.pos.system.service.StockAnalyticsService;
import com.pos.system.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@CrossOrigin
public class StockController {

    private final StockService stockService;
    private final StockAnalyticsService stockAnalyticsService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<StockResponseDto>> getStockByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(stockService.getStockByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/branch/{branchId}/paged")
    public ResponseEntity<Page<StockResponseDto>> getStockByBranchPaged(
            @PathVariable Long branchId,
            @PageableDefault(size = 25, sort = "lastUpdated", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(stockService.getStockByBranch(branchId, pageable));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/branch/{branchId}/item/{itemId}")
    public ResponseEntity<StockResponseDto> getStockByBranchAndItem(@PathVariable Long branchId,
                                                                    @PathVariable Long itemId) {
        return ResponseEntity.ok(stockService.getStockByBranchAndItem(branchId, itemId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/branch/{branchId}/item/{itemId}/details")
    public ResponseEntity<ItemStockDetailsResponse> getItemStockDetails(
            @PathVariable Long branchId, @PathVariable Long itemId) {
        return ResponseEntity.ok(stockService.getItemStockDetails(branchId, itemId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_BATCH_VIEW')")
    @GetMapping("/batches/branch/{branchId}")
    public ResponseEntity<List<StockBatchResponseDto>> getStockBatchesByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(stockService.getStockBatchesByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_BATCH_VIEW')")
    @GetMapping("/batches/branch/{branchId}/paged")
    public ResponseEntity<Page<StockBatchResponseDto>> getStockBatchesByBranchPaged(
            @PathVariable Long branchId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long itemId,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(stockService.getStockBatchesByBranch(branchId, q, itemId, pageable));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_BATCH_VIEW')")
    @GetMapping("/batches/branch/{branchId}/item/{itemId}")
    public ResponseEntity<List<StockBatchResponseDto>> getStockBatchesByBranchAndItem(@PathVariable Long branchId,
                                                                                       @PathVariable Long itemId) {
        return ResponseEntity.ok(stockService.getStockBatchesByBranchAndItem(branchId, itemId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_MOVEMENT_VIEW')")
    @GetMapping("/movements/branch/{branchId}")
    public ResponseEntity<List<StockMovementResponseDto>> getStockMovementsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(stockService.getStockMovementsByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_MOVEMENT_VIEW')")
    @GetMapping("/movements/branch/{branchId}/paged")
    public ResponseEntity<Page<StockMovementResponseDto>> getStockMovementsByBranchPaged(
            @PathVariable Long branchId,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(stockService.getStockMovementsByBranch(branchId, pageable));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_MOVEMENT_VIEW')")
    @GetMapping("/movements/branch/{branchId}/item/{itemId}")
    public ResponseEntity<List<StockMovementResponseDto>> getStockMovementsByBranchAndItem(@PathVariable Long branchId,
                                                                                            @PathVariable Long itemId) {
        return ResponseEntity.ok(stockService.getStockMovementsByBranchAndItem(branchId, itemId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('OPENING_STOCK_CREATE')")
    @PostMapping("/opening")
    public ResponseEntity<List<StockBatchResponseDto>> addOpeningStock(@RequestBody OpeningStockRequestDto dto) {
        return ResponseEntity.ok(stockService.addOpeningStock(dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_ADJUST')")
    @PostMapping("/adjustments")
    public ResponseEntity<StockBatchResponseDto> adjustStock(@RequestBody StockAdjustRequest dto) {
        return ResponseEntity.ok(stockService.adjustStock(dto));
    }

    // Stock Analytics

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}")
    public ResponseEntity<StockAnalyticsResponse> getStockAnalytics(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange,
            @RequestParam(defaultValue = "10") int productLimit,
            @RequestParam(defaultValue = "10") int slowLimit,
            @RequestParam(defaultValue = "30") int slowWarningDays,
            @RequestParam(defaultValue = "90") int slowCriticalDays) {

        return ResponseEntity.ok(stockAnalyticsService.getAnalytics(
                branchId, from, to, dateRange, productLimit, slowLimit, slowWarningDays, slowCriticalDays));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/summary")
    public ResponseEntity<StockSummary> getStockAnalyticsSummary(@PathVariable Long branchId) {
        return ResponseEntity.ok(stockAnalyticsService.getSummary(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/movement")
    public ResponseEntity<List<MovementBucket>> getStockMovementAnalytics(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(stockAnalyticsService.getMovement(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/health")
    public ResponseEntity<InventoryHealth> getStockHealth(@PathVariable Long branchId) {
        return ResponseEntity.ok(stockAnalyticsService.getHealth(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/value")
    public ResponseEntity<InventoryValue> getStockInventoryValue(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(stockAnalyticsService.getInventoryValue(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/top-selling-products")
    public ResponseEntity<List<TopSellingProduct>> getTopSellingStockProducts(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(stockAnalyticsService.getTopSellingProducts(branchId, from, to, dateRange, limit));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/slow-moving-products")
    public ResponseEntity<List<SlowMovingProduct>> getSlowMovingStockProducts(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int slowWarningDays,
            @RequestParam(defaultValue = "90") int slowCriticalDays) {

        return ResponseEntity.ok(stockAnalyticsService.getSlowMovingProducts(
                branchId, from, to, dateRange, limit, slowWarningDays, slowCriticalDays));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/notifications")
    public ResponseEntity<List<StockNotification>> getStockNotifications(@PathVariable Long branchId) {
        return ResponseEntity.ok(stockAnalyticsService.getNotifications(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @PatchMapping("/analytics/branch/{branchId}/notifications/mark-all-read")
    public ResponseEntity<NotificationReadResponse> markStockNotificationsRead(@PathVariable Long branchId) {
        return ResponseEntity.ok(stockAnalyticsService.markAllNotificationsRead(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_TRANSFER_CREATE')")
    @PostMapping("/transfers")
    public ResponseEntity<StockTransferResponseDto> createStockTransfer(@RequestBody StockTransferRequestDto dto) {
        return ResponseEntity.ok(stockService.createStockTransfer(dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_TRANSFER_VIEW')")
    @GetMapping("/transfers/{transferId}")
    public ResponseEntity<StockTransferResponseDto> getStockTransferById(@PathVariable Long transferId) {
        return ResponseEntity.ok(stockService.getStockTransferById(transferId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_TRANSFER_VIEW')")
    @GetMapping("/transfers/branch/{branchId}")
    public ResponseEntity<List<StockTransferResponseDto>> getStockTransfersByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(stockService.getStockTransfersByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_TRANSFER_VIEW')")
    @GetMapping("/transfers/branch/{branchId}/paged")
    public ResponseEntity<Page<StockTransferResponseDto>> getStockTransfersByBranchPaged(
            @PathVariable Long branchId,
            @PageableDefault(size = 25, sort = "transferDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(stockService.getStockTransfersByBranch(branchId, pageable));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_COUNT_CREATE')")
    @PostMapping("/counts")
    public ResponseEntity<StockCountResponseDto> createStockCount(@RequestBody StockCountRequestDto dto) {
        return ResponseEntity.ok(stockService.createStockCount(dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_COUNT_VIEW')")
    @GetMapping("/counts/{stockCountId}")
    public ResponseEntity<StockCountResponseDto> getStockCountById(@PathVariable Long stockCountId) {
        return ResponseEntity.ok(stockService.getStockCountById(stockCountId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_COUNT_VIEW')")
    @GetMapping("/counts/branch/{branchId}")
    public ResponseEntity<List<StockCountResponseDto>> getStockCountsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(stockService.getStockCountsByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_COUNT_VIEW')")
    @GetMapping("/counts/branch/{branchId}/paged")
    public ResponseEntity<Page<StockCountResponseDto>> getStockCountsByBranchPaged(
            @PathVariable Long branchId,
            @PageableDefault(size = 25, sort = "countDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(stockService.getStockCountsByBranch(branchId, pageable));
    }
}
