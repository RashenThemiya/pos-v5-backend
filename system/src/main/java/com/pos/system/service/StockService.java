package com.pos.system.service;

import com.pos.system.dto.stock.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StockService {

    List<StockResponseDto> getStockByBranch(Long branchId);
    Page<StockResponseDto> getStockByBranch(Long branchId, Pageable pageable);
    StockResponseDto getStockByBranchAndItem(Long branchId, Long itemId);
    ItemStockDetailsResponse getItemStockDetails(Long branchId, Long itemId);

    List<StockBatchResponseDto> getStockBatchesByBranch(Long branchId);
    Page<StockBatchResponseDto> getStockBatchesByBranch(Long branchId, Pageable pageable);
    Page<StockBatchResponseDto> getStockBatchesByBranch(Long branchId, String query, Long itemId, Pageable pageable);
    List<StockBatchResponseDto> getStockBatchesByBranchAndItem(Long branchId, Long itemId);

    List<StockMovementResponseDto> getStockMovementsByBranch(Long branchId);
    Page<StockMovementResponseDto> getStockMovementsByBranch(Long branchId, Pageable pageable);
    List<StockMovementResponseDto> getStockMovementsByBranchAndItem(Long branchId, Long itemId);

    List<StockBatchResponseDto> addOpeningStock(OpeningStockRequestDto dto);
    StockBatchResponseDto adjustStock(StockAdjustRequest dto);

    StockTransferResponseDto createStockTransfer(StockTransferRequestDto dto);
    StockTransferResponseDto getStockTransferById(Long transferId);
    List<StockTransferResponseDto> getStockTransfersByBranch(Long branchId);
    Page<StockTransferResponseDto> getStockTransfersByBranch(Long branchId, Pageable pageable);

    StockCountResponseDto createStockCount(StockCountRequestDto dto);
    StockCountResponseDto getStockCountById(Long stockCountId);
    List<StockCountResponseDto> getStockCountsByBranch(Long branchId);
    Page<StockCountResponseDto> getStockCountsByBranch(Long branchId, Pageable pageable);
}
