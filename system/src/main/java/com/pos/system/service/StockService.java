package com.pos.system.service;

import com.pos.system.dto.stock.*;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.stock.*;
import com.pos.system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockTransferRepository stockTransferRepository;
    private final StockTransferItemRepository stockTransferItemRepository;
    private final StockCountRepository stockCountRepository;
    private final StockCountItemRepository stockCountItemRepository;
    private final ItemRepository itemRepository;
    private final BranchRepository branchRepository;

    // ── Stock Levels ──────────────────────────────────────────────────────────

    public List<StockResponse> getStockByBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        return stockRepository.findByBranchId(branchId)
                .stream().map(this::toStockResponse).collect(Collectors.toList());
    }

    public StockResponse getStockByItem(Long branchId, Long itemId) {
        Stock stock = stockRepository.findByBranchIdAndItemId(branchId, itemId)
                .orElseThrow(() -> new RuntimeException("Stock not found for this item in this branch"));
        return toStockResponse(stock);
    }

    // ── Stock Adjustment ──────────────────────────────────────────────────────

    @Transactional
    public StockResponse adjustStock(StockAdjustRequest request) {
        itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new RuntimeException("Item not found with id: " + request.getItemId()));

        // Get or create stock record
        Stock stock = stockRepository.findByBranchIdAndItemId(request.getBranchId(), request.getItemId())
                .orElseGet(() -> {
                    Stock s = new Stock();
                    s.setBranchId(request.getBranchId());
                    s.setItemId(request.getItemId());
                    s.setAvailableQty(BigDecimal.ZERO);
                    s.setDamagedQty(BigDecimal.ZERO);
                    s.setExpiredQty(BigDecimal.ZERO);
                    return s;
                });

        String type = request.getMovementType() != null ? request.getMovementType() : "ADJUSTMENT_IN";

        // Apply adjustment based on type
        switch (type) {
            case "DAMAGE":
                stock.setDamagedQty(stock.getDamagedQty().add(request.getQuantity()));
                stock.setAvailableQty(stock.getAvailableQty().subtract(request.getQuantity()));
                break;
            case "EXPIRED":
                stock.setExpiredQty(stock.getExpiredQty().add(request.getQuantity()));
                stock.setAvailableQty(stock.getAvailableQty().subtract(request.getQuantity()));
                break;
            case "ADJUSTMENT_OUT":
                stock.setAvailableQty(stock.getAvailableQty().subtract(request.getQuantity().abs()));
                break;
            default: // ADJUSTMENT_IN
                stock.setAvailableQty(stock.getAvailableQty().add(request.getQuantity()));
                break;
        }

        stock.setLastUpdated(LocalDateTime.now());
        stockRepository.save(stock);

        // Record movement
        StockMovement movement = new StockMovement();
        movement.setBranchId(request.getBranchId());
        movement.setItemId(request.getItemId());
        movement.setMovementType(type);
        movement.setQuantity(request.getQuantity());
        movement.setNote(request.getNote());
        movement.setCreatedBy(request.getCreatedBy());
        movement.setCreatedAt(LocalDateTime.now());
        stockMovementRepository.save(movement);

        return toStockResponse(stock);
    }

    // ── Stock Movements ───────────────────────────────────────────────────────

    public List<StockMovementResponse> getMovementsByBranch(Long branchId) {
        return stockMovementRepository.findByBranchIdOrderByCreatedAtDesc(branchId)
                .stream().map(this::toMovementResponse).collect(Collectors.toList());
    }

    public List<StockMovementResponse> getMovementsByItem(Long branchId, Long itemId) {
        return stockMovementRepository.findByBranchIdAndItemIdOrderByCreatedAtDesc(branchId, itemId)
                .stream().map(this::toMovementResponse).collect(Collectors.toList());
    }

    // ── Stock Transfer ────────────────────────────────────────────────────────

    @Transactional
    public StockTransferResponse createTransfer(StockTransferRequest request) {
        if (request.getFromBranchId().equals(request.getToBranchId())) {
            throw new RuntimeException("From branch and To branch cannot be the same");
        }

        branchRepository.findById(request.getFromBranchId())
                .orElseThrow(() -> new RuntimeException("From branch not found"));
        branchRepository.findById(request.getToBranchId())
                .orElseThrow(() -> new RuntimeException("To branch not found"));

        // Validate stock availability
        for (StockTransferRequest.StockTransferItemRequest item : request.getItems()) {
            Stock stock = stockRepository.findByBranchIdAndItemId(request.getFromBranchId(), item.getItemId())
                    .orElseThrow(() -> new RuntimeException("No stock found for item id: " + item.getItemId()));
            if (stock.getAvailableQty().compareTo(item.getQuantity()) < 0) {
                throw new RuntimeException("Insufficient stock for item id: " + item.getItemId()
                        + ". Available: " + stock.getAvailableQty());
            }
        }

        // Create transfer record
        StockTransfer transfer = new StockTransfer();
        transfer.setFromBranchId(request.getFromBranchId());
        transfer.setToBranchId(request.getToBranchId());
        transfer.setCreatedBy(request.getCreatedBy());
        transfer.setNote(request.getNote());
        transfer.setStatus("COMPLETED");
        transfer.setTransferDate(LocalDateTime.now());
        transfer = stockTransferRepository.save(transfer);

        final Long transferId = transfer.getTransferId();

        // Process each item
        for (StockTransferRequest.StockTransferItemRequest itemReq : request.getItems()) {
            // Save transfer item
            StockTransferItem transferItem = new StockTransferItem();
            transferItem.setTransferId(transferId);
            transferItem.setItemId(itemReq.getItemId());
            transferItem.setQuantity(itemReq.getQuantity());
            transferItem.setInternalBatchBarcode(itemReq.getInternalBatchBarcode());
            stockTransferItemRepository.save(transferItem);

            // Deduct from source branch
            Stock fromStock = stockRepository.findByBranchIdAndItemId(request.getFromBranchId(), itemReq.getItemId()).get();
            fromStock.setAvailableQty(fromStock.getAvailableQty().subtract(itemReq.getQuantity()));
            fromStock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(fromStock);

            // Add to destination branch
            Stock toStock = stockRepository.findByBranchIdAndItemId(request.getToBranchId(), itemReq.getItemId())
                    .orElseGet(() -> {
                        Stock s = new Stock();
                        s.setBranchId(request.getToBranchId());
                        s.setItemId(itemReq.getItemId());
                        s.setAvailableQty(BigDecimal.ZERO);
                        s.setDamagedQty(BigDecimal.ZERO);
                        s.setExpiredQty(BigDecimal.ZERO);
                        return s;
                    });
            toStock.setAvailableQty(toStock.getAvailableQty().add(itemReq.getQuantity()));
            toStock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(toStock);

            // Record movements
            recordMovement(request.getFromBranchId(), itemReq.getItemId(), "TRANSFER_OUT",
                    itemReq.getQuantity().negate(), request.getCreatedBy(), "Transfer to branch " + request.getToBranchId(), transferId);
            recordMovement(request.getToBranchId(), itemReq.getItemId(), "TRANSFER_IN",
                    itemReq.getQuantity(), request.getCreatedBy(), "Transfer from branch " + request.getFromBranchId(), transferId);
        }

        return toTransferResponse(transfer);
    }

    public List<StockTransferResponse> getTransfersByBranch(Long branchId) {
        return stockTransferRepository.findByFromBranchIdOrToBranchIdOrderByTransferDateDesc(branchId, branchId)
                .stream().map(this::toTransferResponse).collect(Collectors.toList());
    }

    public StockTransferResponse getTransferById(Long transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found with id: " + transferId));
        return toTransferResponse(transfer);
    }

    // ── Stock Count ───────────────────────────────────────────────────────────

    @Transactional
    public StockCountResponse createStockCount(StockCountRequest request) {
        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + request.getBranchId()));

        StockCount stockCount = new StockCount();
        stockCount.setBranchId(request.getBranchId());
        stockCount.setCreatedBy(request.getCreatedBy());
        stockCount.setNote(request.getNote());
        stockCount.setStatus("DRAFT");
        stockCount.setCountDate(LocalDateTime.now());
        stockCount = stockCountRepository.save(stockCount);

        final Long countId = stockCount.getStockCountId();

        for (StockCountRequest.StockCountItemRequest itemReq : request.getItems()) {
            itemRepository.findById(itemReq.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found with id: " + itemReq.getItemId()));

            // Get system qty
            BigDecimal systemQty = stockRepository.findByBranchIdAndItemId(request.getBranchId(), itemReq.getItemId())
                    .map(Stock::getAvailableQty).orElse(BigDecimal.ZERO);

            BigDecimal diff = itemReq.getCountedQty().subtract(systemQty);

            StockCountItem countItem = new StockCountItem();
            countItem.setStockCountId(countId);
            countItem.setItemId(itemReq.getItemId());
            countItem.setSystemQty(systemQty);
            countItem.setCountedQty(itemReq.getCountedQty());
            countItem.setDifferenceQty(diff);
            countItem.setNote(itemReq.getNote());
            stockCountItemRepository.save(countItem);
        }

        return toCountResponse(stockCount);
    }

    @Transactional
    public StockCountResponse approveStockCount(Long stockCountId, Long approvedBy) {
        StockCount stockCount = stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new RuntimeException("Stock count not found with id: " + stockCountId));

        if (!"DRAFT".equals(stockCount.getStatus())) {
            throw new RuntimeException("Only DRAFT stock counts can be approved");
        }

        // Apply counted quantities to actual stock
        List<StockCountItem> items = stockCountItemRepository.findByStockCountId(stockCountId);
        for (StockCountItem item : items) {
            Stock stock = stockRepository.findByBranchIdAndItemId(stockCount.getBranchId(), item.getItemId())
                    .orElseGet(() -> {
                        Stock s = new Stock();
                        s.setBranchId(stockCount.getBranchId());
                        s.setItemId(item.getItemId());
                        s.setAvailableQty(BigDecimal.ZERO);
                        s.setDamagedQty(BigDecimal.ZERO);
                        s.setExpiredQty(BigDecimal.ZERO);
                        return s;
                    });
            stock.setAvailableQty(item.getCountedQty());
            stock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(stock);

            // Record adjustment movement
            if (item.getDifferenceQty().compareTo(BigDecimal.ZERO) != 0) {
                recordMovement(stockCount.getBranchId(), item.getItemId(), "STOCK_COUNT_ADJUSTMENT",
                        item.getDifferenceQty(), approvedBy, "Stock count #" + stockCountId, stockCountId);
            }
        }

        stockCount.setStatus("APPROVED");
        stockCount.setApprovedBy(approvedBy);
        return toCountResponse(stockCountRepository.save(stockCount));
    }

    public List<StockCountResponse> getStockCountsByBranch(Long branchId) {
        return stockCountRepository.findByBranchIdOrderByCountDateDesc(branchId)
                .stream().map(this::toCountResponse).collect(Collectors.toList());
    }

    public StockCountResponse getStockCountById(Long stockCountId) {
        StockCount stockCount = stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new RuntimeException("Stock count not found with id: " + stockCountId));
        return toCountResponse(stockCount);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private void recordMovement(Long branchId, Long itemId, String type, BigDecimal qty,
                                Long createdBy, String note, Long refId) {
        StockMovement movement = new StockMovement();
        movement.setBranchId(branchId);
        movement.setItemId(itemId);
        movement.setMovementType(type);
        movement.setQuantity(qty);
        movement.setCreatedBy(createdBy);
        movement.setNote(note);
        movement.setRefId(refId);
        movement.setCreatedAt(LocalDateTime.now());
        stockMovementRepository.save(movement);
    }

    private StockResponse toStockResponse(Stock stock) {
        StockResponse response = new StockResponse();
        response.setStockId(stock.getStockId());
        response.setBranchId(stock.getBranchId());
        response.setItemId(stock.getItemId());
        response.setAvailableQty(stock.getAvailableQty());
        response.setDamagedQty(stock.getDamagedQty());
        response.setExpiredQty(stock.getExpiredQty());
        response.setLastUpdated(stock.getLastUpdated());
        itemRepository.findById(stock.getItemId()).ifPresent(item -> {
            response.setItemName(item.getName());
            response.setItemSku(item.getSku());
        });
        return response;
    }

    private StockMovementResponse toMovementResponse(StockMovement movement) {
        StockMovementResponse response = new StockMovementResponse();
        response.setMovementId(movement.getMovementId());
        response.setBranchId(movement.getBranchId());
        response.setItemId(movement.getItemId());
        response.setMovementType(movement.getMovementType());
        response.setInternalBatchBarcode(movement.getInternalBatchBarcode());
        response.setQuantity(movement.getQuantity());
        response.setUnitCost(movement.getUnitCost());
        response.setUnitPrice(movement.getUnitPrice());
        response.setRefTable(movement.getRefTable());
        response.setRefId(movement.getRefId());
        response.setNote(movement.getNote());
        response.setCreatedBy(movement.getCreatedBy());
        response.setCreatedAt(movement.getCreatedAt());
        if (movement.getItemId() != null) {
            itemRepository.findById(movement.getItemId())
                    .ifPresent(item -> response.setItemName(item.getName()));
        }
        return response;
    }

    private StockTransferResponse toTransferResponse(StockTransfer transfer) {
        StockTransferResponse response = new StockTransferResponse();
        response.setTransferId(transfer.getTransferId());
        response.setFromBranchId(transfer.getFromBranchId());
        response.setToBranchId(transfer.getToBranchId());
        response.setTransferDate(transfer.getTransferDate());
        response.setStatus(transfer.getStatus());
        response.setCreatedBy(transfer.getCreatedBy());
        response.setNote(transfer.getNote());

        branchRepository.findById(transfer.getFromBranchId())
                .ifPresent(b -> response.setFromBranchName(b.getName()));
        branchRepository.findById(transfer.getToBranchId())
                .ifPresent(b -> response.setToBranchName(b.getName()));

        List<StockTransferResponse.ItemDetail> items = stockTransferItemRepository
                .findByTransferId(transfer.getTransferId())
                .stream().map(ti -> {
                    StockTransferResponse.ItemDetail detail = new StockTransferResponse.ItemDetail();
                    detail.setTransferItemId(ti.getTransferItemId());
                    detail.setItemId(ti.getItemId());
                    detail.setQuantity(ti.getQuantity());
                    detail.setInternalBatchBarcode(ti.getInternalBatchBarcode());
                    itemRepository.findById(ti.getItemId()).ifPresent(item -> {
                        detail.setItemName(item.getName());
                        detail.setItemSku(item.getSku());
                    });
                    return detail;
                }).collect(Collectors.toList());
        response.setItems(items);
        return response;
    }

    private StockCountResponse toCountResponse(StockCount stockCount) {
        StockCountResponse response = new StockCountResponse();
        response.setStockCountId(stockCount.getStockCountId());
        response.setBranchId(stockCount.getBranchId());
        response.setCountDate(stockCount.getCountDate());
        response.setStatus(stockCount.getStatus());
        response.setCreatedBy(stockCount.getCreatedBy());
        response.setApprovedBy(stockCount.getApprovedBy());
        response.setNote(stockCount.getNote());

        List<StockCountResponse.ItemCountDetail> items = stockCountItemRepository
                .findByStockCountId(stockCount.getStockCountId())
                .stream().map(ci -> {
                    StockCountResponse.ItemCountDetail detail = new StockCountResponse.ItemCountDetail();
                    detail.setStockCountItemId(ci.getStockCountItemId());
                    detail.setItemId(ci.getItemId());
                    detail.setSystemQty(ci.getSystemQty());
                    detail.setCountedQty(ci.getCountedQty());
                    detail.setDifferenceQty(ci.getDifferenceQty());
                    detail.setNote(ci.getNote());
                    itemRepository.findById(ci.getItemId()).ifPresent(item -> {
                        detail.setItemName(item.getName());
                        detail.setItemSku(item.getSku());
                    });
                    return detail;
                }).collect(Collectors.toList());
        response.setItems(items);
        return response;
    }
}
