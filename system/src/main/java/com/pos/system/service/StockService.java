package com.pos.system.service;

import com.pos.system.dto.stock.*;

import java.util.List;

public interface StockService {

    List<StockResponseDto> getStockByBranch(Long branchId);
    StockResponseDto getStockByBranchAndItem(Long branchId, Long itemId);

    List<StockBatchResponseDto> getStockBatchesByBranch(Long branchId);
    List<StockBatchResponseDto> getStockBatchesByBranchAndItem(Long branchId, Long itemId);

    List<StockMovementResponseDto> getStockMovementsByBranch(Long branchId);
    List<StockMovementResponseDto> getStockMovementsByBranchAndItem(Long branchId, Long itemId);

    List<StockBatchResponseDto> addOpeningStock(OpeningStockRequestDto dto);
    StockBatchResponseDto adjustStock(StockAdjustRequest dto);

    StockTransferResponseDto createStockTransfer(StockTransferRequestDto dto);
    StockTransferResponseDto getStockTransferById(Long transferId);
    List<StockTransferResponseDto> getStockTransfersByBranch(Long branchId);

    StockCountResponseDto createStockCount(StockCountRequestDto dto);
    StockCountResponseDto getStockCountById(Long stockCountId);
    List<StockCountResponseDto> getStockCountsByBranch(Long branchId);
}
