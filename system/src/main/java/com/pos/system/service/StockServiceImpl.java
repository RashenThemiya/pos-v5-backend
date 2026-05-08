package com.pos.system.service;

import com.pos.system.dto.stock.*;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.stock.*;
import com.pos.system.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class StockServiceImpl implements StockService {

    private final StockRepository stockRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockTransferRepository stockTransferRepository;
    private final StockTransferItemRepository stockTransferItemRepository;
    private final StockCountRepository stockCountRepository;
    private final StockCountItemRepository stockCountItemRepository;

    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final UnitConversionService unitConversionService;

    @Override
    public List<StockResponseDto> getStockByBranch(Long branchId) {
        return stockRepository.findByBranchIdOrderByLastUpdatedDesc(branchId)
                .stream()
                .map(this::mapStock)
                .toList();
    }

    @Override
    public StockResponseDto getStockByBranchAndItem(Long branchId, Long itemId) {
        Stock stock = stockRepository.findByBranchIdAndItemId(branchId, itemId)
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        return mapStock(stock);
    }

    @Override
    public List<StockBatchResponseDto> getStockBatchesByBranch(Long branchId) {
        return stockBatchRepository.findByBranchIdOrderByCreatedAtDesc(branchId)
                .stream()
                .map(this::mapBatch)
                .toList();
    }

    @Override
    public List<StockBatchResponseDto> getStockBatchesByBranchAndItem(Long branchId, Long itemId) {
        return stockBatchRepository.findByBranchIdAndItemIdOrderByCreatedAtDesc(branchId, itemId)
                .stream()
                .map(this::mapBatch)
                .toList();
    }

    @Override
    public List<StockMovementResponseDto> getStockMovementsByBranch(Long branchId) {
        return stockMovementRepository.findByBranchIdOrderByCreatedAtDesc(branchId)
                .stream()
                .map(this::mapMovement)
                .toList();
    }

    @Override
    public List<StockMovementResponseDto> getStockMovementsByBranchAndItem(Long branchId, Long itemId) {
        return stockMovementRepository.findByBranchIdAndItemIdOrderByCreatedAtDesc(branchId, itemId)
                .stream()
                .map(this::mapMovement)
                .toList();
    }

    @Override
    public List<StockBatchResponseDto> addOpeningStock(OpeningStockRequestDto dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new RuntimeException("Opening stock items cannot be empty");
        }

        for (OpeningStockItemRequestDto itemDto : dto.getItems()) {
            validateItemAndUnit(dto.getBranchId(), itemDto.getItemId(), itemDto.getUnitId());

            BigDecimal qty = nvlQty(itemDto.getQuantity());
            BigDecimal qtyBase = unitConversionService.toBaseQty(itemDto.getItemId(), itemDto.getUnitId(), qty);

            ItemUnit baseUnit = itemUnitRepository.findByItemIdAndIsBaseUnitTrue(itemDto.getItemId())
                    .orElseThrow(() -> new RuntimeException("Base unit not found for item: " + itemDto.getItemId()));

            Stock stock = stockRepository.findByBranchIdAndItemId(dto.getBranchId(), itemDto.getItemId())
                    .orElseGet(() -> {
                        Stock s = new Stock();
                        s.setBranchId(dto.getBranchId());
                        s.setItemId(itemDto.getItemId());
                        s.setUnitId(baseUnit.getUnitId());
                        s.setAvailableQty(BigDecimal.ZERO);
                        s.setDamagedQty(BigDecimal.ZERO);
                        s.setExpiredQty(BigDecimal.ZERO);
                        return s;
                    });

            stock.setAvailableQty(nvlQty(stock.getAvailableQty()).add(qtyBase));
            stock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(stock);

            String internalBatchBarcode = generateInternalBatchBarcode(dto.getBranchId(), itemDto.getItemId());

            StockBatch batch = new StockBatch();
            batch.setBranchId(dto.getBranchId());
            batch.setItemId(itemDto.getItemId());
            batch.setSupplyProductId(0L);
            batch.setUnitId(itemDto.getUnitId());
            batch.setReceivedQty(qty);
            batch.setReceivedBaseQty(qtyBase);
            batch.setQtyRemaining(qtyBase);
            batch.setInternalBatchBarcode(internalBatchBarcode);
            batch.setBatchNo(itemDto.getBatchNo());
            batch.setSupplierBatchBarcode(itemDto.getSupplierBatchBarcode());
            batch.setExpiryDate(itemDto.getExpiryDate());
            batch.setCostPrice(itemDto.getCostPrice());
            batch.setSellingPrice(itemDto.getSellingPrice());
            batch.setCreatedAt(LocalDateTime.now());
            stockBatchRepository.save(batch);

            StockMovement movement = new StockMovement();
            movement.setBranchId(dto.getBranchId());
            movement.setMovementType("OPENING");
            movement.setItemId(itemDto.getItemId());
            movement.setUnitId(itemDto.getUnitId());
            movement.setInternalBatchBarcode(internalBatchBarcode);
            movement.setQuantity(qtyBase);
            movement.setUnitCost(itemDto.getCostPrice());
            movement.setUnitPrice(itemDto.getSellingPrice());
            movement.setRefTable("opening_stock");
            movement.setRefId(0L);
            movement.setNote(itemDto.getNote());
            movement.setCreatedBy(dto.getCreatedBy());
            movement.setCreatedAt(LocalDateTime.now());
            stockMovementRepository.save(movement);
        }

        return stockBatchRepository.findByBranchIdOrderByCreatedAtDesc(dto.getBranchId())
                .stream()
                .limit(dto.getItems().size())
                .map(this::mapBatch)
                .toList();
    }

    @Override
    public StockTransferResponseDto createStockTransfer(StockTransferRequestDto dto) {
        if (dto.getFromBranchId().equals(dto.getToBranchId())) {
            throw new RuntimeException("From and to branch cannot be same");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new RuntimeException("Transfer items cannot be empty");
        }

        StockTransfer transfer = new StockTransfer();
        transfer.setFromBranchId(dto.getFromBranchId());
        transfer.setToBranchId(dto.getToBranchId());
        transfer.setTransferDate(LocalDateTime.now());
        transfer.setStatus("COMPLETED");
        transfer.setCreatedBy(dto.getCreatedBy());
        transfer.setNote(dto.getNote());

        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        for (StockTransferItemRequestDto itemDto : dto.getItems()) {
            StockBatch sourceBatch = stockBatchRepository
                    .findByBranchIdAndInternalBatchBarcode(dto.getFromBranchId(), itemDto.getInternalBatchBarcode())
                    .orElseThrow(() -> new RuntimeException("Source batch not found: " + itemDto.getInternalBatchBarcode()));

            BigDecimal qty = nvlQty(itemDto.getQuantity());

            if (sourceBatch.getQtyRemaining().compareTo(qty) < 0) {
                throw new RuntimeException("Insufficient batch qty for batch: " + sourceBatch.getInternalBatchBarcode());
            }

            sourceBatch.setQtyRemaining(sourceBatch.getQtyRemaining().subtract(qty));
            stockBatchRepository.save(sourceBatch);

            Stock fromStock = stockRepository.findByBranchIdAndItemId(dto.getFromBranchId(), itemDto.getItemId())
                    .orElseThrow(() -> new RuntimeException("Source stock not found"));
            fromStock.setAvailableQty(nvlQty(fromStock.getAvailableQty()).subtract(qty));
            fromStock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(fromStock);

            ItemUnit baseUnit = itemUnitRepository.findByItemIdAndIsBaseUnitTrue(itemDto.getItemId())
                    .orElseThrow(() -> new RuntimeException("Base unit not found for item: " + itemDto.getItemId()));

            Stock toStock = stockRepository.findByBranchIdAndItemId(dto.getToBranchId(), itemDto.getItemId())
                    .orElseGet(() -> {
                        Stock s = new Stock();
                        s.setBranchId(dto.getToBranchId());
                        s.setItemId(itemDto.getItemId());
                        s.setUnitId(baseUnit.getUnitId());
                        s.setAvailableQty(BigDecimal.ZERO);
                        s.setDamagedQty(BigDecimal.ZERO);
                        s.setExpiredQty(BigDecimal.ZERO);
                        return s;
                    });
            toStock.setAvailableQty(nvlQty(toStock.getAvailableQty()).add(qty));
            toStock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(toStock);

            StockBatch destBatch = new StockBatch();
            destBatch.setBranchId(dto.getToBranchId());
            destBatch.setItemId(sourceBatch.getItemId());
            destBatch.setSupplyProductId(sourceBatch.getSupplyProductId());
            destBatch.setUnitId(sourceBatch.getUnitId());
            destBatch.setReceivedQty(sourceBatch.getReceivedQty());
            destBatch.setReceivedBaseQty(sourceBatch.getReceivedBaseQty());
            destBatch.setQtyRemaining(qty);
            destBatch.setInternalBatchBarcode(generateInternalBatchBarcode(dto.getToBranchId(), sourceBatch.getItemId()));
            destBatch.setBatchNo(sourceBatch.getBatchNo());
            destBatch.setSupplierBatchBarcode(sourceBatch.getSupplierBatchBarcode());
            destBatch.setExpiryDate(sourceBatch.getExpiryDate());
            destBatch.setCostPrice(sourceBatch.getCostPrice());
            destBatch.setSellingPrice(sourceBatch.getSellingPrice());
            destBatch.setCreatedAt(LocalDateTime.now());
            stockBatchRepository.save(destBatch);

            StockTransferItem transferItem = new StockTransferItem();
            transferItem.setTransferId(savedTransfer.getTransferId());
            transferItem.setItemId(itemDto.getItemId());
            transferItem.setInternalBatchBarcode(itemDto.getInternalBatchBarcode());
            transferItem.setQuantity(qty);
            stockTransferItemRepository.save(transferItem);

            StockMovement outMovement = new StockMovement();
            outMovement.setBranchId(dto.getFromBranchId());
            outMovement.setMovementType("TRANSFER_OUT");
            outMovement.setItemId(itemDto.getItemId());
            outMovement.setUnitId(sourceBatch.getUnitId());
            outMovement.setInternalBatchBarcode(sourceBatch.getInternalBatchBarcode());
            outMovement.setQuantity(qty);
            outMovement.setUnitCost(sourceBatch.getCostPrice());
            outMovement.setUnitPrice(sourceBatch.getSellingPrice());
            outMovement.setRefTable("stock_transfers");
            outMovement.setRefId(savedTransfer.getTransferId());
            outMovement.setNote(dto.getNote());
            outMovement.setCreatedBy(dto.getCreatedBy());
            outMovement.setCreatedAt(LocalDateTime.now());
            stockMovementRepository.save(outMovement);

            StockMovement inMovement = new StockMovement();
            inMovement.setBranchId(dto.getToBranchId());
            inMovement.setMovementType("TRANSFER_IN");
            inMovement.setItemId(itemDto.getItemId());
            inMovement.setUnitId(sourceBatch.getUnitId());
            inMovement.setInternalBatchBarcode(destBatch.getInternalBatchBarcode());
            inMovement.setQuantity(qty);
            inMovement.setUnitCost(sourceBatch.getCostPrice());
            inMovement.setUnitPrice(sourceBatch.getSellingPrice());
            inMovement.setRefTable("stock_transfers");
            inMovement.setRefId(savedTransfer.getTransferId());
            inMovement.setNote(dto.getNote());
            inMovement.setCreatedBy(dto.getCreatedBy());
            inMovement.setCreatedAt(LocalDateTime.now());
            stockMovementRepository.save(inMovement);
        }

        return getStockTransferById(savedTransfer.getTransferId());
    }

    @Override
    public StockTransferResponseDto getStockTransferById(Long transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found"));

        List<StockTransferItemResponseDto> items = stockTransferItemRepository.findByTransferId(transferId)
                .stream()
                .map(i -> StockTransferItemResponseDto.builder()
                        .transferItemId(i.getTransferItemId())
                        .transferId(i.getTransferId())
                        .itemId(i.getItemId())
                        .internalBatchBarcode(i.getInternalBatchBarcode())
                        .quantity(i.getQuantity())
                        .build())
                .toList();

        return StockTransferResponseDto.builder()
                .transferId(transfer.getTransferId())
                .fromBranchId(transfer.getFromBranchId())
                .toBranchId(transfer.getToBranchId())
                .transferDate(transfer.getTransferDate())
                .status(transfer.getStatus())
                .createdBy(transfer.getCreatedBy())
                .note(transfer.getNote())
                .items(items)
                .build();
    }

    @Override
    public StockCountResponseDto createStockCount(StockCountRequestDto dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new RuntimeException("Stock count items cannot be empty");
        }

        StockCount count = new StockCount();
        count.setBranchId(dto.getBranchId());
        count.setCountDate(LocalDateTime.now());
        count.setStatus("COUNTED");
        count.setCreatedBy(dto.getCreatedBy());
        count.setNote(dto.getNote());

        StockCount savedCount = stockCountRepository.save(count);

        for (StockCountItemRequestDto itemDto : dto.getItems()) {
            ItemUnit baseUnit = itemUnitRepository.findByItemIdAndIsBaseUnitTrue(itemDto.getItemId())
                    .orElseThrow(() -> new RuntimeException("Base unit not found for item: " + itemDto.getItemId()));

            Stock stock = stockRepository.findByBranchIdAndItemId(dto.getBranchId(), itemDto.getItemId())
                    .orElseGet(() -> {
                        Stock s = new Stock();
                        s.setBranchId(dto.getBranchId());
                        s.setItemId(itemDto.getItemId());
                        s.setUnitId(baseUnit.getUnitId());
                        s.setAvailableQty(BigDecimal.ZERO);
                        s.setDamagedQty(BigDecimal.ZERO);
                        s.setExpiredQty(BigDecimal.ZERO);
                        return s;
                    });

            BigDecimal systemQty = nvlQty(stock.getAvailableQty());
            BigDecimal countedQty = nvlQty(itemDto.getCountedQty());
            BigDecimal diff = countedQty.subtract(systemQty);

            StockCountItem countItem = new StockCountItem();
            countItem.setStockCountId(savedCount.getStockCountId());
            countItem.setItemId(itemDto.getItemId());
            countItem.setSystemQty(systemQty);
            countItem.setCountedQty(countedQty);
            countItem.setDifferenceQty(diff);
            countItem.setNote(itemDto.getNote());
            stockCountItemRepository.save(countItem);
        }

        return getStockCountById(savedCount.getStockCountId());
    }

    @Override
    public StockCountResponseDto getStockCountById(Long stockCountId) {
        StockCount count = stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new RuntimeException("Stock count not found"));

        List<StockCountItemResponseDto> items = stockCountItemRepository.findByStockCountId(stockCountId)
                .stream()
                .map(i -> StockCountItemResponseDto.builder()
                        .stockCountItemId(i.getStockCountItemId())
                        .stockCountId(i.getStockCountId())
                        .itemId(i.getItemId())
                        .systemQty(i.getSystemQty())
                        .countedQty(i.getCountedQty())
                        .differenceQty(i.getDifferenceQty())
                        .note(i.getNote())
                        .build())
                .toList();

        return StockCountResponseDto.builder()
                .stockCountId(count.getStockCountId())
                .branchId(count.getBranchId())
                .countDate(count.getCountDate())
                .status(count.getStatus())
                .createdBy(count.getCreatedBy())
                .approvedBy(count.getApprovedBy())
                .note(count.getNote())
                .items(items)
                .build();
    }

    @Override
    public List<StockCountResponseDto> getStockCountsByBranch(Long branchId) {
        return stockCountRepository.findByBranchIdOrderByCountDateDesc(branchId)
                .stream()
                .map(c -> getStockCountById(c.getStockCountId()))
                .toList();
    }

    private void validateItemAndUnit(Long branchId, Long itemId, Long unitId) {
        Item item = itemRepository.findByItemIdAndIsActiveTrue(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found or inactive: " + itemId));

        if (!item.getBranchId().equals(branchId)) {
            throw new RuntimeException("Item does not belong to branch: " + itemId);
        }

        ItemUnit itemUnit = itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(itemId, unitId)
                .orElseThrow(() -> new RuntimeException("Unit not found or inactive for itemId=" + itemId + ", unitId=" + unitId));

        if (!itemUnit.getBranchId().equals(branchId)) {
            throw new RuntimeException("Unit does not belong to branch for unitId=" + unitId);
        }
    }

    private String generateInternalBatchBarcode(Long branchId, Long itemId) {
        for (int i = 0; i < 10; i++) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String code = "BT-" + branchId + "-" + itemId + "-" + timestamp + "-" +
                    UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            boolean exists = stockBatchRepository.findByBranchIdAndInternalBatchBarcode(branchId, code).isPresent();
            if (!exists) {
                return code;
            }
        }
        throw new RuntimeException("Failed to generate internal batch barcode");
    }

    private StockResponseDto mapStock(Stock stock) {
        return StockResponseDto.builder()
                .stockId(stock.getStockId())
                .branchId(stock.getBranchId())
                .itemId(stock.getItemId())
                .unitId(stock.getUnitId())
                .availableQty(stock.getAvailableQty())
                .damagedQty(stock.getDamagedQty())
                .expiredQty(stock.getExpiredQty())
                .lastUpdated(stock.getLastUpdated())
                .build();
    }

    private StockBatchResponseDto mapBatch(StockBatch batch) {
        return StockBatchResponseDto.builder()
                .stockBatchId(batch.getStockBatchId())
                .branchId(batch.getBranchId())
                .itemId(batch.getItemId())
                .supplyProductId(batch.getSupplyProductId())
                .unitId(batch.getUnitId())
                .receivedQty(batch.getReceivedQty())
                .receivedBaseQty(batch.getReceivedBaseQty())
                .qtyRemaining(batch.getQtyRemaining())
                .internalBatchBarcode(batch.getInternalBatchBarcode())
                .batchNo(batch.getBatchNo())
                .supplierBatchBarcode(batch.getSupplierBatchBarcode())
                .expiryDate(batch.getExpiryDate())
                .costPrice(batch.getCostPrice())
                .sellingPrice(batch.getSellingPrice())
                .createdAt(batch.getCreatedAt())
                .build();
    }

    private StockMovementResponseDto mapMovement(StockMovement movement) {
        return StockMovementResponseDto.builder()
                .movementId(movement.getMovementId())
                .branchId(movement.getBranchId())
                .movementType(movement.getMovementType())
                .itemId(movement.getItemId())
                .unitId(movement.getUnitId())
                .internalBatchBarcode(movement.getInternalBatchBarcode())
                .quantity(movement.getQuantity())
                .unitCost(movement.getUnitCost())
                .unitPrice(movement.getUnitPrice())
                .refTable(movement.getRefTable())
                .refId(movement.getRefId())
                .note(movement.getNote())
                .createdBy(movement.getCreatedBy())
                .createdAt(movement.getCreatedAt())
                .build();
    }

    private BigDecimal nvlQty(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}