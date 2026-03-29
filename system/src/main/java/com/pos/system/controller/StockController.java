package com.pos.system.controller;

import com.pos.system.dto.common.ApiResponse;
import com.pos.system.dto.stock.*;
import com.pos.system.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
@CrossOrigin
public class StockController {

    private final StockService stockService;

    // ── Stock Levels ──────────────────────────────────────────────────────────

    // GET /api/stock/branch/{branchId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<StockResponse>>> getStockByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success("Stock fetched successfully", stockService.getStockByBranch(branchId)));
    }

    // GET /api/stock/branch/{branchId}/item/{itemId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/branch/{branchId}/item/{itemId}")
    public ResponseEntity<ApiResponse<StockResponse>> getStockByItem(
            @PathVariable Long branchId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success("Stock fetched successfully", stockService.getStockByItem(branchId, itemId)));
    }

    // ── Stock Adjustment ──────────────────────────────────────────────────────

    // POST /api/stock/adjust
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_ADJUST')")
    @PostMapping("/adjust")
    public ResponseEntity<ApiResponse<StockResponse>> adjustStock(@Valid @RequestBody StockAdjustRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted successfully", stockService.adjustStock(request)));
    }

    // ── Stock Movements ───────────────────────────────────────────────────────

    // GET /api/stock/movements/branch/{branchId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/movements/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> getMovementsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success("Movements fetched successfully", stockService.getMovementsByBranch(branchId)));
    }

    // GET /api/stock/movements/branch/{branchId}/item/{itemId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/movements/branch/{branchId}/item/{itemId}")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> getMovementsByItem(
            @PathVariable Long branchId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success("Item movements fetched successfully", stockService.getMovementsByItem(branchId, itemId)));
    }

    // ── Stock Transfer ────────────────────────────────────────────────────────

    // POST /api/stock/transfers
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_TRANSFER')")
    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<StockTransferResponse>> createTransfer(@Valid @RequestBody StockTransferRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Stock transfer completed successfully", stockService.createTransfer(request)));
    }

    // GET /api/stock/transfers/branch/{branchId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/transfers/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getTransfersByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success("Transfers fetched successfully", stockService.getTransfersByBranch(branchId)));
    }

    // GET /api/stock/transfers/{transferId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/transfers/{transferId}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransferById(@PathVariable Long transferId) {
        return ResponseEntity.ok(ApiResponse.success("Transfer fetched successfully", stockService.getTransferById(transferId)));
    }

    // ── Stock Count ───────────────────────────────────────────────────────────

    // POST /api/stock/counts
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_COUNT')")
    @PostMapping("/counts")
    public ResponseEntity<ApiResponse<StockCountResponse>> createStockCount(@Valid @RequestBody StockCountRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Stock count created successfully", stockService.createStockCount(request)));
    }

    // PATCH /api/stock/counts/{stockCountId}/approve
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_COUNT_APPROVE')")
    @PatchMapping("/counts/{stockCountId}/approve")
    public ResponseEntity<ApiResponse<StockCountResponse>> approveStockCount(
            @PathVariable Long stockCountId,
            @RequestParam Long approvedBy) {
        return ResponseEntity.ok(ApiResponse.success("Stock count approved successfully", stockService.approveStockCount(stockCountId, approvedBy)));
    }

    // GET /api/stock/counts/branch/{branchId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/counts/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<StockCountResponse>>> getStockCountsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success("Stock counts fetched successfully", stockService.getStockCountsByBranch(branchId)));
    }

    // GET /api/stock/counts/{stockCountId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/counts/{stockCountId}")
    public ResponseEntity<ApiResponse<StockCountResponse>> getStockCountById(@PathVariable Long stockCountId) {
        return ResponseEntity.ok(ApiResponse.success("Stock count fetched successfully", stockService.getStockCountById(stockCountId)));
    }
}
