package com.pos.system.controller;

import com.pos.system.dto.stock.*;
import com.pos.system.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@CrossOrigin
public class StockController {

    private final StockService stockService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<StockResponseDto>> getStockByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(stockService.getStockByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_VIEW')")
    @GetMapping("/branch/{branchId}/item/{itemId}")
    public ResponseEntity<StockResponseDto> getStockByBranchAndItem(@PathVariable Long branchId,
                                                                    @PathVariable Long itemId) {
        return ResponseEntity.ok(stockService.getStockByBranchAndItem(branchId, itemId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('STOCK_BATCH_VIEW')")
    @GetMapping("/batches/branch/{branchId}")
    public ResponseEntity<List<StockBatchResponseDto>> getStockBatchesByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(stockService.getStockBatchesByBranch(branchId));
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
}
