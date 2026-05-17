package com.pos.system.service;

import com.pos.system.dto.stock.*;
import com.pos.system.model.catalog.Brand;
import com.pos.system.model.catalog.Category;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.stock.*;
import com.pos.system.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
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
                        return s;
                    });

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
            batch.setAvailableQty(qtyBase);
            batch.setDamagedQty(BigDecimal.ZERO);
            batch.setExpiredQty(BigDecimal.ZERO);
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
    public StockBatchResponseDto adjustStock(StockAdjustRequest dto) {
        validateItemAndUnit(dto.getBranchId(), dto.getItemId(), dto.getUnitId());

        BigDecimal qty = nvlQty(dto.getQuantity());
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Adjustment quantity must be greater than zero");
        }

        BigDecimal qtyBase = unitConversionService.toBaseQty(dto.getItemId(), dto.getUnitId(), qty);
        String adjustmentType = dto.getAdjustmentType() != null
                ? dto.getAdjustmentType().trim().toUpperCase()
                : "";

        StockBatch batch = stockBatchRepository
                .findByBranchIdAndInternalBatchBarcode(dto.getBranchId(), dto.getInternalBatchBarcode())
                .orElseThrow(() -> new RuntimeException("Stock batch not found: " + dto.getInternalBatchBarcode()));

        if (!batch.getItemId().equals(dto.getItemId())) {
            throw new RuntimeException("Batch does not belong to item: " + dto.getItemId());
        }

        switch (adjustmentType) {
            case "AVAILABLE_TO_DAMAGED" -> moveAvailableToDamaged(batch, qtyBase);
            case "AVAILABLE_TO_EXPIRED" -> moveAvailableToExpired(batch, qtyBase);
            case "DAMAGED_TO_AVAILABLE" -> moveDamagedToAvailable(batch, qtyBase);
            case "EXPIRED_TO_AVAILABLE" -> moveExpiredToAvailable(batch, qtyBase);
            case "AVAILABLE_OUT" -> removeAvailable(batch, qtyBase);
            case "AVAILABLE_IN" -> addAvailable(batch, qtyBase);
            default -> throw new RuntimeException("Unsupported adjustment type: " + dto.getAdjustmentType());
        }

        StockBatch savedBatch = stockBatchRepository.save(batch);

        stockRepository.findByBranchIdAndItemId(dto.getBranchId(), dto.getItemId()).ifPresent(stock -> {
            stock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(stock);
        });

        StockMovement movement = new StockMovement();
        movement.setBranchId(dto.getBranchId());
        movement.setMovementType(adjustmentType);
        movement.setItemId(dto.getItemId());
        movement.setUnitId(dto.getUnitId());
        movement.setInternalBatchBarcode(dto.getInternalBatchBarcode());
        movement.setQuantity(qtyBase);
        movement.setRefTable("stock_batches");
        movement.setRefId(savedBatch.getStockBatchId());
        movement.setNote(dto.getNote());
        movement.setCreatedBy(dto.getCreatedBy());
        movement.setCreatedAt(LocalDateTime.now());
        stockMovementRepository.save(movement);

        return mapBatch(savedBatch);
    }

    @Override
    public StockTransferResponseDto createStockTransfer(StockTransferRequestDto dto) {
        if (dto.getFromBranchId() == null || dto.getToBranchId() == null) {
            throw new RuntimeException("From and to branch are required");
        }
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
            StockBatch sourceBatch = findSourceTransferBatch(dto.getFromBranchId(), itemDto);

            BigDecimal qty = nvlQty(itemDto.getQuantity());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Transfer quantity must be greater than zero");
            }

            if (!sourceBatch.getItemId().equals(itemDto.getItemId())) {
                throw new RuntimeException("Batch does not belong to item: " + itemDto.getItemId());
            }

            if (nvlQty(sourceBatch.getAvailableQty()).compareTo(qty) < 0) {
                throw new RuntimeException("Insufficient batch qty for batch: " + sourceBatch.getInternalBatchBarcode());
            }

            sourceBatch.setQtyRemaining(nvlQty(sourceBatch.getQtyRemaining()).subtract(qty));
            sourceBatch.setAvailableQty(nvlQty(sourceBatch.getAvailableQty()).subtract(qty));
            stockBatchRepository.save(sourceBatch);

            Stock fromStock = stockRepository.findByBranchIdAndItemId(dto.getFromBranchId(), itemDto.getItemId())
                    .orElseThrow(() -> new RuntimeException("Source stock not found"));
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
                        return s;
                    });
            toStock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(toStock);

            StockBatch destBatch = new StockBatch();
            destBatch.setBranchId(dto.getToBranchId());
            destBatch.setItemId(sourceBatch.getItemId());
            destBatch.setSupplyProductId(sourceBatch.getSupplyProductId());
            destBatch.setUnitId(sourceBatch.getUnitId());
            destBatch.setReceivedQty(qty);
            destBatch.setReceivedBaseQty(qty);
            destBatch.setQtyRemaining(qty);
            destBatch.setAvailableQty(qty);
            destBatch.setDamagedQty(BigDecimal.ZERO);
            destBatch.setExpiredQty(BigDecimal.ZERO);
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
            transferItem.setInternalBatchBarcode(sourceBatch.getInternalBatchBarcode());
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

    private StockBatch findSourceTransferBatch(Long fromBranchId, StockTransferItemRequestDto itemDto) {
        StockBatch sourceBatch;
        if (itemDto.getStockBatchId() != null) {
            sourceBatch = stockBatchRepository.findById(itemDto.getStockBatchId())
                    .orElseThrow(() -> new RuntimeException("Source batch not found: " + itemDto.getStockBatchId()));
        } else if (itemDto.getInternalBatchBarcode() != null && !itemDto.getInternalBatchBarcode().isBlank()) {
            sourceBatch = stockBatchRepository
                    .findByBranchIdAndInternalBatchBarcode(fromBranchId, itemDto.getInternalBatchBarcode())
                    .orElseThrow(() -> new RuntimeException("Source batch not found: " + itemDto.getInternalBatchBarcode()));
        } else {
            throw new RuntimeException("stockBatchId or internalBatchBarcode is required");
        }

        if (!sourceBatch.getBranchId().equals(fromBranchId)) {
            throw new RuntimeException("Source batch does not belong to from branch");
        }

        return sourceBatch;
    }

    @Override
    public StockTransferResponseDto getStockTransferById(Long transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found"));

        return mapStockTransfer(transfer);
    }

    @Override
    public List<StockTransferResponseDto> getStockTransfersByBranch(Long branchId) {
        return stockTransferRepository.findByFromBranchIdOrToBranchIdOrderByTransferDateDesc(branchId, branchId)
                .stream()
                .map(this::mapStockTransfer)
                .toList();
    }

    private StockTransferResponseDto mapStockTransfer(StockTransfer transfer) {
        List<StockTransferItemResponseDto> items = stockTransferItemRepository.findByTransferId(transfer.getTransferId())
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
                        return s;
                    });

            BigDecimal systemQty = getBatchQtyTotal(dto.getBranchId(), itemDto.getItemId(), StockQtyType.AVAILABLE);
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
        Item item = itemRepository.findByItemIdAndBranchId(stock.getItemId(), stock.getBranchId()).orElse(null);
        Category category = item != null && item.getCategoryId() != null
                ? categoryRepository.findByCategoryIdAndBranchId(item.getCategoryId(), stock.getBranchId()).orElse(null)
                : null;
        Category parentCategory = category != null && category.getParentId() != null
                ? categoryRepository.findByCategoryIdAndBranchId(category.getParentId(), stock.getBranchId()).orElse(null)
                : null;
        Brand brand = item != null && item.getBrandId() != null
                ? brandRepository.findByBrandIdAndBranchId(item.getBrandId(), stock.getBranchId()).orElse(null)
                : null;
        List<StockResponseDto.UnitStockDto> unitStocks = item != null
                ? itemUnitRepository.findByItemId(stock.getItemId())
                        .stream()
                        .filter(itemUnit -> itemUnit.getBranchId().equals(stock.getBranchId()))
                        .map(itemUnit -> mapUnitStock(stock, itemUnit))
                        .toList()
                : List.of();

        StockResponseDto.StockResponseDtoBuilder builder = StockResponseDto.builder()
                .stockId(stock.getStockId())
                .branchId(stock.getBranchId())
                .itemId(stock.getItemId())
                .unitStocks(unitStocks)
                .lastUpdated(stock.getLastUpdated());

        if (item != null) {
            builder.itemSku(item.getSku())
                    .itemName(item.getName())
                    .itemImage(item.getImage())
                    .itemIsWeighed(item.getIsWeighed())
                    .itemIsActive(item.getIsActive())
                    .minStock(item.getMinStock())
                    .maxStock(item.getMaxStock())
                    .categoryId(item.getCategoryId())
                    .brandId(item.getBrandId());
        }

        if (category != null) {
            builder.categoryName(category.getName())
                    .categoryIsActive(category.getIsActive());

            if (parentCategory != null) {
                builder.parentCategoryId(parentCategory.getCategoryId())
                        .parentCategoryName(parentCategory.getName())
                        .parentCategoryIsActive(parentCategory.getIsActive())
                        .subCategoryId(category.getCategoryId())
                        .subCategoryName(category.getName())
                        .subCategoryIsActive(category.getIsActive());
            } else {
                builder.parentCategoryId(category.getCategoryId())
                        .parentCategoryName(category.getName())
                        .parentCategoryIsActive(category.getIsActive());
            }
        }

        if (brand != null) {
            builder.brandName(brand.getName())
                    .brandIsActive(brand.getIsActive());
        }

        return builder.build();
    }

    private StockResponseDto.UnitStockDto mapUnitStock(Stock stock, ItemUnit unit) {
        List<StockBatch> batches = stockBatchRepository.findByBranchIdAndItemIdOrderByCreatedAtDesc(
                stock.getBranchId(),
                stock.getItemId()
        ).stream()
                .filter(batch -> batch.getUnitId().equals(unit.getUnitId()))
                .toList();
        BigDecimal availableBaseQty = batches.stream()
                .map(batch -> batch.getAvailableQty() != null ? batch.getAvailableQty() : batch.getQtyRemaining())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal damagedBaseQty = batches.stream()
                .map(batch -> nvlQty(batch.getDamagedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expiredBaseQty = batches.stream()
                .map(batch -> nvlQty(batch.getExpiredQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return StockResponseDto.UnitStockDto.builder()
                .unitId(unit.getUnitId())
                .unitName(unit.getUnitName())
                .unitBarcode(unit.getBarcode())
                .multiplierToBase(unit.getMultiplierToBase())
                .defaultSellingPrice(unit.getDefaultSellingPrice())
                .isBaseUnit(unit.getIsBaseUnit())
                .isActive(unit.getIsActive())
                .availableQty(toResponseUnitQty(availableBaseQty, unit))
                .damagedQty(toResponseUnitQty(damagedBaseQty, unit))
                .expiredQty(toResponseUnitQty(expiredBaseQty, unit))
                .build();
    }

    private BigDecimal toResponseUnitQty(BigDecimal baseQty, ItemUnit unit) {
        BigDecimal qty = nvlQty(baseQty);
        if (unit == null || unit.getMultiplierToBase() == null
                || unit.getMultiplierToBase().compareTo(BigDecimal.ZERO) <= 0) {
            return qty;
        }
        return qty.divide(unit.getMultiplierToBase(), 4, RoundingMode.HALF_UP);
    }

    private void moveAvailableToDamaged(StockBatch batch, BigDecimal qtyBase) {
        requireQty(nvlQty(batch.getAvailableQty()), qtyBase, "available");
        batch.setAvailableQty(nvlQty(batch.getAvailableQty()).subtract(qtyBase));
        batch.setQtyRemaining(nvlQty(batch.getQtyRemaining()).subtract(qtyBase));
        batch.setDamagedQty(nvlQty(batch.getDamagedQty()).add(qtyBase));
    }

    private void moveAvailableToExpired(StockBatch batch, BigDecimal qtyBase) {
        requireQty(nvlQty(batch.getAvailableQty()), qtyBase, "available");
        batch.setAvailableQty(nvlQty(batch.getAvailableQty()).subtract(qtyBase));
        batch.setQtyRemaining(nvlQty(batch.getQtyRemaining()).subtract(qtyBase));
        batch.setExpiredQty(nvlQty(batch.getExpiredQty()).add(qtyBase));
    }

    private void moveDamagedToAvailable(StockBatch batch, BigDecimal qtyBase) {
        requireQty(nvlQty(batch.getDamagedQty()), qtyBase, "damaged");
        batch.setDamagedQty(nvlQty(batch.getDamagedQty()).subtract(qtyBase));
        batch.setAvailableQty(nvlQty(batch.getAvailableQty()).add(qtyBase));
        batch.setQtyRemaining(nvlQty(batch.getQtyRemaining()).add(qtyBase));
    }

    private void moveExpiredToAvailable(StockBatch batch, BigDecimal qtyBase) {
        requireQty(nvlQty(batch.getExpiredQty()), qtyBase, "expired");
        batch.setExpiredQty(nvlQty(batch.getExpiredQty()).subtract(qtyBase));
        batch.setAvailableQty(nvlQty(batch.getAvailableQty()).add(qtyBase));
        batch.setQtyRemaining(nvlQty(batch.getQtyRemaining()).add(qtyBase));
    }

    private void removeAvailable(StockBatch batch, BigDecimal qtyBase) {
        requireQty(nvlQty(batch.getAvailableQty()), qtyBase, "available");
        batch.setAvailableQty(nvlQty(batch.getAvailableQty()).subtract(qtyBase));
        batch.setQtyRemaining(nvlQty(batch.getQtyRemaining()).subtract(qtyBase));
    }

    private void addAvailable(StockBatch batch, BigDecimal qtyBase) {
        batch.setAvailableQty(nvlQty(batch.getAvailableQty()).add(qtyBase));
        batch.setQtyRemaining(nvlQty(batch.getQtyRemaining()).add(qtyBase));
    }

    private void requireQty(BigDecimal currentQty, BigDecimal requestedQty, String stockType) {
        if (currentQty.compareTo(requestedQty) < 0) {
            throw new RuntimeException("Not enough " + stockType + " stock in batch");
        }
    }

    private StockBatchResponseDto mapBatch(StockBatch batch) {
        Item item = itemRepository.findByItemIdAndBranchId(batch.getItemId(), batch.getBranchId()).orElse(null);
        ItemUnit unit = itemUnitRepository.findById(batch.getUnitId())
                .filter(itemUnit -> itemUnit.getItemId().equals(batch.getItemId()))
                .filter(itemUnit -> itemUnit.getBranchId().equals(batch.getBranchId()))
                .orElse(null);

        BigDecimal qtyRemaining = toResponseUnitQty(batch.getQtyRemaining(), unit);
        BigDecimal availableQty = toResponseUnitQty(
                batch.getAvailableQty() != null ? batch.getAvailableQty() : batch.getQtyRemaining(),
                unit
        );
        BigDecimal damagedQty = toResponseUnitQty(batch.getDamagedQty(), unit);
        BigDecimal expiredQty = toResponseUnitQty(batch.getExpiredQty(), unit);

        return StockBatchResponseDto.builder()
                .stockBatchId(batch.getStockBatchId())
                .branchId(batch.getBranchId())
                .itemId(batch.getItemId())
                .itemSku(item != null ? item.getSku() : null)
                .itemName(item != null ? item.getName() : null)
                .supplyProductId(batch.getSupplyProductId())
                .unitId(batch.getUnitId())
                .unitName(unit != null ? unit.getUnitName() : null)
                .unitBarcode(unit != null ? unit.getBarcode() : null)
                .unitMultiplierToBase(unit != null ? unit.getMultiplierToBase() : null)
                .unitIsBaseUnit(unit != null ? unit.getIsBaseUnit() : null)
                .unitIsActive(unit != null ? unit.getIsActive() : null)
                .receivedQty(batch.getReceivedQty())
                .qtyRemaining(qtyRemaining)
                .availableQty(availableQty)
                .damagedQty(damagedQty)
                .expiredQty(expiredQty)
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

    private BigDecimal getBatchQtyTotal(Long branchId, Long itemId, StockQtyType type) {
        return stockBatchRepository.findByBranchIdAndItemId(branchId, itemId)
                .stream()
                .map(batch -> switch (type) {
                    case AVAILABLE -> nvlQty(batch.getAvailableQty() != null ? batch.getAvailableQty() : batch.getQtyRemaining());
                    case DAMAGED -> nvlQty(batch.getDamagedQty());
                    case EXPIRED -> nvlQty(batch.getExpiredQty());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private enum StockQtyType {
        AVAILABLE,
        DAMAGED,
        EXPIRED
    }
}
