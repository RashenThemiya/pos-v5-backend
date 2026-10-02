package com.pos.system.service;

import com.pos.system.dto.stock.*;
import com.pos.system.model.catalog.Brand;
import com.pos.system.model.catalog.Category;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.ItemVariant;
import com.pos.system.model.catalog.ItemVariantAttribute;
import com.pos.system.model.catalog.UnitMaster;
import com.pos.system.model.stock.*;
import com.pos.system.model.supplier.Supply;
import com.pos.system.model.supplier.SupplyProduct;
import com.pos.system.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
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
    private final ItemVariantRepository itemVariantRepository;
    private final ItemVariantAttributeRepository itemVariantAttributeRepository;
    private final UnitMasterRepository unitMasterRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final UserRepository userRepository;
    private final UnitConversionService unitConversionService;

    @Autowired
    private SupplyProductRepository supplyProductRepository;

    @Autowired
    private SupplyRepository supplyRepository;

    @Override
    public List<StockResponseDto> getStockByBranch(Long branchId) {
        return stockRepository.findByBranchIdOrderByLastUpdatedDesc(branchId)
                .stream()
                .map(this::mapStock)
                .toList();
    }

    @Override
    public Page<StockResponseDto> getStockByBranch(Long branchId, Pageable pageable) {
        return stockRepository.findByBranchId(branchId, pageable)
                .map(this::mapStock);
    }

    @Override
    public StockResponseDto getStockByBranchAndItem(Long branchId, Long itemId) {
        Stock stock = findStock(branchId, itemId, null)
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        return mapStock(stock);
    }

    @Override
    public ItemStockDetailsResponse getItemStockDetails(Long branchId, Long itemId) {
        Item item = itemRepository.findById(itemId)
                .filter(value -> value.getBranchId().equals(branchId))
                .orElseThrow(() -> new RuntimeException("Item not found in branch"));

        List<ItemUnit> units = itemUnitRepository.findByItemId(itemId);
        ItemUnit baseUnit = units.stream().filter(unit -> Boolean.TRUE.equals(unit.getIsBaseUnit()))
                .findFirst().orElseThrow(() -> new RuntimeException("Base unit not found for item: " + itemId));
        List<ItemVariant> itemVariants = itemVariantRepository.findByItemIdOrderByVariantIdAsc(itemId);

        List<ItemStockDetailsResponse.VariantStockDto> variants;
        if (itemVariants.isEmpty()) {
            variants = List.of(buildVariantStockDetails(item, null, units));
        } else {
            variants = itemVariants.stream()
                    .map(variant -> buildVariantStockDetails(item, variant, units))
                    .toList();
        }

        return ItemStockDetailsResponse.builder()
                .itemId(itemId)
                .branchId(branchId)
                .baseUnit(ItemStockDetailsResponse.BaseUnitDto.builder()
                        .unitId(baseUnit.getUnitId())
                        .unitName(resolveUnitName(baseUnit))
                        .multiplierToBase(baseUnit.getMultiplierToBase())
                        .build())
                .supportsReservedStock(false)
                .variants(variants)
                .build();
    }

    private ItemStockDetailsResponse.VariantStockDto buildVariantStockDetails(
            Item item, ItemVariant variant, List<ItemUnit> units) {
        Long variantId = variant != null ? variant.getVariantId() : null;
        List<StockBatch> batches = findBatches(item.getBranchId(), item.getItemId(), variantId);
        BigDecimal available = sumBatchQty(batches, StockBatch::getAvailableQty);
        BigDecimal damaged = sumBatchQty(batches, StockBatch::getDamagedQty);
        BigDecimal expired = sumBatchQty(batches, StockBatch::getExpiredQty);
        BigDecimal total = available.add(damaged).add(expired);

        List<ItemStockDetailsResponse.AttributeDto> attributes = variant == null ? List.of()
                : itemVariantAttributeRepository.findByVariantIdOrderByAttributeNameAsc(variantId).stream()
                .map(attribute -> ItemStockDetailsResponse.AttributeDto.builder()
                        .name(attribute.getAttributeName()).value(attribute.getAttributeValue()).build())
                .toList();

        return ItemStockDetailsResponse.VariantStockDto.builder()
                .variantId(variantId)
                .variantName(variant != null && "variant=standard".equals(variant.getCombinationSignature())
                        ? "Standard Item"
                        : (variant != null ? resolveVariantLabel(variantId) : item.getName()))
                .sku(variant != null
                        ? (variant.getSku() != null ? variant.getSku() : variant.getVariantSku())
                        : item.getSku())
                .imageUrl(variant != null && variant.getImage() != null ? variant.getImage() : item.getImage())
                .isActive(variant != null ? variant.getIsActive() : item.getIsActive())
                .attributes(attributes)
                .stock(ItemStockDetailsResponse.StockTotalsDto.builder()
                        .totalBaseQty(total).availableBaseQty(available).reservedBaseQty(null)
                        .damagedBaseQty(damaged).expiredBaseQty(expired).build())
                .unitStocks(units.stream().map(unit -> ItemStockDetailsResponse.UnitStockDto.builder()
                        .unitId(unit.getUnitId()).unitName(resolveUnitName(unit))
                        .multiplierToBase(unit.getMultiplierToBase()).isBaseUnit(unit.getIsBaseUnit())
                        .totalBaseQty(total).availableBaseQty(available).build()).toList())
                .batches(batches.stream().map(this::mapDetailsBatch)
                        .filter(batch -> "ACTIVE".equals(batch.getStatus()) || "NEAR_EXPIRY".equals(batch.getStatus()))
                        .toList())
                .build();
    }

    private BigDecimal sumBatchQty(List<StockBatch> batches,
                                   java.util.function.Function<StockBatch, BigDecimal> getter) {
        return batches.stream().map(getter).map(this::nvlQty).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ItemStockDetailsResponse.BatchDto mapDetailsBatch(StockBatch batch) {
        ItemUnit receivedUnit = itemUnitRepository.findById(batch.getUnitId()).orElse(null);
        return ItemStockDetailsResponse.BatchDto.builder()
                .stockBatchId(batch.getStockBatchId()).variantId(batch.getVariantId())
                .batchNo(batch.getBatchNo()).internalBatchBarcode(batch.getInternalBatchBarcode())
                .supplierBatchBarcode(batch.getSupplierBatchBarcode())
                .receivedDate(batch.getCreatedAt() != null ? batch.getCreatedAt().toLocalDate() : null)
                .expiryDate(batch.getExpiryDate()).receivedUnitId(batch.getUnitId())
                .receivedUnitName(receivedUnit != null ? resolveUnitName(receivedUnit) : null)
                .receivedUnitMultiplier(receivedUnit != null ? receivedUnit.getMultiplierToBase() : null)
                .originalReceivedQty(batch.getReceivedQty()).originalBaseQty(batch.getReceivedBaseQty())
                .availableBaseQty(nvlQty(batch.getAvailableQty())).reservedBaseQty(null)
                .damagedBaseQty(nvlQty(batch.getDamagedQty())).expiredBaseQty(nvlQty(batch.getExpiredQty()))
                .status(resolveBatchStatus(batch)).build();
    }

    private String resolveBatchStatus(StockBatch batch) {
        BigDecimal currentQty = nvlQty(batch.getAvailableQty())
                .add(nvlQty(batch.getDamagedQty())).add(nvlQty(batch.getExpiredQty()));
        if (currentQty.compareTo(BigDecimal.ZERO) <= 0) return "DEPLETED";
        if (batch.getExpiryDate() == null) return "ACTIVE";
        LocalDate today = LocalDate.now();
        if (batch.getExpiryDate().isBefore(today)) return "EXPIRED";
        if (!batch.getExpiryDate().isAfter(today.plusDays(30))) return "NEAR_EXPIRY";
        return "ACTIVE";
    }

    @Override
    public List<StockBatchResponseDto> getStockBatchesByBranch(Long branchId) {
        return stockBatchRepository.findByBranchIdOrderByCreatedAtDesc(branchId)
                .stream()
                .map(this::mapBatch)
                .toList();
    }

    @Override
    public Page<StockBatchResponseDto> getStockBatchesByBranch(Long branchId, Pageable pageable) {
        return stockBatchRepository.findByBranchId(branchId, pageable)
                .map(this::mapBatch);
    }

    @Override
    public Page<StockBatchResponseDto> getStockBatchesByBranch(Long branchId, String query, Long itemId, Pageable pageable) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (itemId != null && normalizedQuery.isEmpty()) {
            return stockBatchRepository.findByBranchIdAndItemId(branchId, itemId, pageable)
                    .map(this::mapBatch);
        }

        if (itemId != null) {
            return stockBatchRepository.searchByBranchAndItemAndBatchText(branchId, itemId, normalizedQuery, pageable)
                    .map(this::mapBatch);
        }

        if (normalizedQuery.isEmpty()) {
            return getStockBatchesByBranch(branchId, pageable);
        }

        return stockBatchRepository.searchByBranchAndBatchText(branchId, normalizedQuery, pageable)
                .map(this::mapBatch);
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
    public Page<StockMovementResponseDto> getStockMovementsByBranch(Long branchId, Pageable pageable) {
        return stockMovementRepository.findByBranchId(branchId, pageable)
                .map(this::mapMovement);
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
            validateItemUnitAndVariant(dto.getBranchId(), itemDto.getItemId(), itemDto.getUnitId(), itemDto.getVariantId());

            BigDecimal qty = nvlQty(itemDto.getQuantity());
            BigDecimal qtyBase = unitConversionService.toBaseQty(itemDto.getItemId(), itemDto.getUnitId(), qty);

            ItemUnit baseUnit = itemUnitRepository.findByItemIdAndIsBaseUnitTrue(itemDto.getItemId())
                    .orElseThrow(() -> new RuntimeException("Base unit not found for item: " + itemDto.getItemId()));

            Stock stock = findStock(dto.getBranchId(), itemDto.getItemId(), itemDto.getVariantId())
                    .orElseGet(() -> {
                        Stock s = new Stock();
                        s.setBranchId(dto.getBranchId());
                        s.setItemId(itemDto.getItemId());
                        s.setVariantId(itemDto.getVariantId());
                        s.setUnitId(baseUnit.getUnitId());
                        return s;
                    });

            stock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(stock);

            String internalBatchBarcode = generateInternalBatchBarcode(dto.getBranchId(), itemDto.getItemId());

            StockBatch batch = new StockBatch();
            batch.setBranchId(dto.getBranchId());
            batch.setItemId(itemDto.getItemId());
            batch.setVariantId(itemDto.getVariantId());
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
            movement.setVariantId(itemDto.getVariantId());
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
        Long variantId = resolveBatchVariantId(dto.getBranchId(), dto.getItemId(), dto.getVariantId(), dto.getInternalBatchBarcode());
        validateItemUnitAndVariant(dto.getBranchId(), dto.getItemId(), dto.getUnitId(), variantId);

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
        if (!sameVariant(batch.getVariantId(), variantId)) {
            throw new RuntimeException("Batch does not belong to selected variant");
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

        findStock(dto.getBranchId(), dto.getItemId(), variantId).ifPresent(stock -> {
            stock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(stock);
        });

        StockMovement movement = new StockMovement();
        movement.setBranchId(dto.getBranchId());
        movement.setMovementType(adjustmentType);
        movement.setItemId(dto.getItemId());
        movement.setVariantId(variantId);
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

            BigDecimal requestedQty = nvlQty(itemDto.getQuantity());
            if (requestedQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Transfer quantity must be greater than zero");
            }

            Long sourceItemId = sourceBatch.getItemId();
            Long sourceVariantId = sourceBatch.getVariantId() != null ? sourceBatch.getVariantId() : itemDto.getVariantId();
            Long transferUnitId = itemDto.getUnitId() != null ? itemDto.getUnitId() : sourceBatch.getUnitId();

            validateItemUnitAndVariant(dto.getFromBranchId(), sourceItemId, transferUnitId, sourceVariantId);

            if (itemDto.getItemId() != null && !sourceBatch.getItemId().equals(itemDto.getItemId())) {
                throw new RuntimeException("Batch does not belong to item: " + itemDto.getItemId());
            }
            if (!sameVariant(sourceBatch.getVariantId(), sourceVariantId)) {
                throw new RuntimeException("Batch does not belong to selected variant");
            }

            BigDecimal qtyBase = itemDto.getUnitId() != null
                    ? unitConversionService.toBaseQty(sourceItemId, transferUnitId, requestedQty)
                    : requestedQty;
            if (qtyBase.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Transfer quantity must be greater than zero");
            }

            if (nvlQty(sourceBatch.getAvailableQty()).compareTo(qtyBase) < 0) {
                throw new RuntimeException("Insufficient batch qty for batch: " + sourceBatch.getInternalBatchBarcode());
            }

            Item sourceItem = findActiveItemInBranch(dto.getFromBranchId(), sourceItemId);
            Item destinationItem = ensureDestinationItem(sourceItem, dto.getToBranchId());
            ItemVariant sourceVariant = findSourceVariant(sourceItemId, sourceVariantId);
            ItemVariant destinationVariant = sourceVariant != null
                    ? ensureDestinationVariant(sourceVariant, destinationItem)
                    : null;
            ItemUnit sourceUnit = resolveTransferSourceUnit(dto.getFromBranchId(), sourceItemId, transferUnitId);
            ItemUnit destinationUnit = ensureDestinationUnit(sourceUnit, destinationItem);
            BigDecimal destinationUnitQty = toResponseUnitQty(qtyBase, destinationUnit);

            sourceBatch.setQtyRemaining(nvlQty(sourceBatch.getQtyRemaining()).subtract(qtyBase));
            sourceBatch.setAvailableQty(nvlQty(sourceBatch.getAvailableQty()).subtract(qtyBase));
            stockBatchRepository.save(sourceBatch);

            Stock fromStock = findStock(dto.getFromBranchId(), sourceItemId, sourceVariantId)
                    .orElseThrow(() -> new RuntimeException("Source stock not found"));
            fromStock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(fromStock);

            ItemUnit destinationBaseUnit = itemUnitRepository.findByItemIdAndIsBaseUnitTrue(destinationItem.getItemId())
                    .orElseThrow(() -> new RuntimeException("Base unit not found for item: " + destinationItem.getItemId()));
            Long destinationVariantId = destinationVariant != null ? destinationVariant.getVariantId() : null;

            Stock toStock = findStock(dto.getToBranchId(), destinationItem.getItemId(), destinationVariantId)
                    .orElseGet(() -> {
                        Stock s = new Stock();
                        s.setBranchId(dto.getToBranchId());
                        s.setItemId(destinationItem.getItemId());
                        s.setVariantId(destinationVariantId);
                        s.setUnitId(destinationBaseUnit.getUnitId());
                        return s;
                    });
            toStock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(toStock);

            StockBatch destBatch = new StockBatch();
            destBatch.setBranchId(dto.getToBranchId());
            destBatch.setItemId(destinationItem.getItemId());
            destBatch.setVariantId(destinationVariantId);
            destBatch.setSupplyProductId(0L);
            destBatch.setUnitId(destinationUnit.getUnitId());
            destBatch.setReceivedQty(destinationUnitQty);
            destBatch.setReceivedBaseQty(qtyBase);
            destBatch.setQtyRemaining(qtyBase);
            destBatch.setAvailableQty(qtyBase);
            destBatch.setDamagedQty(BigDecimal.ZERO);
            destBatch.setExpiredQty(BigDecimal.ZERO);
            destBatch.setInternalBatchBarcode(generateInternalBatchBarcode(dto.getToBranchId(), destinationItem.getItemId()));
            destBatch.setBatchNo(sourceBatch.getBatchNo());
            destBatch.setSupplierBatchBarcode(sourceBatch.getSupplierBatchBarcode());
            destBatch.setExpiryDate(sourceBatch.getExpiryDate());
            destBatch.setCostPrice(sourceBatch.getCostPrice());
            destBatch.setSellingPrice(sourceBatch.getSellingPrice());
            destBatch.setCreatedAt(LocalDateTime.now());
            stockBatchRepository.save(destBatch);

            StockTransferItem transferItem = new StockTransferItem();
            transferItem.setTransferId(savedTransfer.getTransferId());
            transferItem.setItemId(sourceItemId);
            transferItem.setVariantId(sourceVariantId);
            transferItem.setUnitId(sourceUnit.getUnitId());
            transferItem.setInternalBatchBarcode(sourceBatch.getInternalBatchBarcode());
            transferItem.setQuantity(qtyBase);
            stockTransferItemRepository.save(transferItem);

            StockMovement outMovement = new StockMovement();
            outMovement.setBranchId(dto.getFromBranchId());
            outMovement.setMovementType("TRANSFER_OUT");
            outMovement.setItemId(sourceItemId);
            outMovement.setVariantId(sourceVariantId);
            outMovement.setUnitId(sourceUnit.getUnitId());
            outMovement.setInternalBatchBarcode(sourceBatch.getInternalBatchBarcode());
            outMovement.setQuantity(qtyBase);
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
            inMovement.setItemId(destinationItem.getItemId());
            inMovement.setVariantId(destinationVariantId);
            inMovement.setUnitId(destinationUnit.getUnitId());
            inMovement.setInternalBatchBarcode(destBatch.getInternalBatchBarcode());
            inMovement.setQuantity(qtyBase);
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

    private Item findActiveItemInBranch(Long branchId, Long itemId) {
        return itemRepository.findByItemIdAndBranchId(itemId, branchId)
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .orElseThrow(() -> new RuntimeException("Active item not found in branch: " + itemId));
    }

    private Item ensureDestinationItem(Item sourceItem, Long toBranchId) {
        java.util.Optional<Item> existing = itemRepository.findByBranchIdAndSku(toBranchId, sourceItem.getSku());
        if (existing.isPresent()) {
            Item item = existing.get();
            if (!Boolean.TRUE.equals(item.getIsActive())) {
                item.setIsActive(true);
                item.setUpdatedAt(LocalDateTime.now());
                item = itemRepository.save(item);
            }
            return item;
        }

        Item destination = new Item();
        destination.setBranchId(toBranchId);
        destination.setSku(sourceItem.getSku());
        destination.setName(sourceItem.getName());
        destination.setImage(sourceItem.getImage());
        destination.setCategoryId(resolveMatchingCategoryId(sourceItem.getCategoryId(), toBranchId));
        destination.setBrandId(resolveMatchingBrandId(sourceItem.getBrandId(), toBranchId));
        destination.setIsWeighed(sourceItem.getIsWeighed());
        destination.setMinStock(sourceItem.getMinStock());
        destination.setMaxStock(sourceItem.getMaxStock());
        destination.setScaleBarcodePrefix(sourceItem.getScaleBarcodePrefix());
        destination.setIsActive(true);
        destination.setCreatedAt(LocalDateTime.now());
        destination.setUpdatedAt(LocalDateTime.now());

        Item savedDestination = itemRepository.save(destination);
        copyDestinationUnits(sourceItem, savedDestination);
        copyDestinationVariants(sourceItem, savedDestination);
        return savedDestination;
    }

    private ItemVariant findSourceVariant(Long itemId, Long variantId) {
        if (variantId == null) {
            return null;
        }

        return itemVariantRepository.findByVariantIdAndItemId(variantId, itemId)
                .filter(variant -> Boolean.TRUE.equals(variant.getIsActive()))
                .orElseThrow(() -> new RuntimeException("Active source variant not found: " + variantId));
    }

    private ItemVariant ensureDestinationVariant(ItemVariant sourceVariant, Item destinationItem) {
        java.util.Optional<ItemVariant> existing = findDestinationVariant(sourceVariant, destinationItem);
        if (existing.isPresent()) {
            ItemVariant variant = existing.get();
            if (!Boolean.TRUE.equals(variant.getIsActive())) {
                variant.setIsActive(true);
                variant.setUpdatedAt(LocalDateTime.now());
                variant = itemVariantRepository.save(variant);
            }
            return variant;
        }

        return copyDestinationVariant(sourceVariant, destinationItem);
    }

    private java.util.Optional<ItemVariant> findDestinationVariant(ItemVariant sourceVariant, Item destinationItem) {
        if (sourceVariant == null || destinationItem == null) {
            return java.util.Optional.empty();
        }

        List<ItemVariant> destinationVariants = itemVariantRepository
                .findByItemIdOrderByVariantIdAsc(destinationItem.getItemId())
                .stream()
                .filter(variant -> Boolean.TRUE.equals(variant.getIsActive()))
                .toList();

        java.util.Optional<ItemVariant> byCombination = destinationVariants.stream()
                .filter(variant -> sameText(variant.getCombinationSignature(), sourceVariant.getCombinationSignature()))
                .findFirst();
        if (byCombination.isPresent()) {
            return byCombination;
        }

        java.util.Optional<ItemVariant> bySku = destinationVariants.stream()
                .filter(variant -> sameText(variant.getSku(), sourceVariant.getSku()))
                .findFirst();
        if (bySku.isPresent()) {
            return bySku;
        }

        return destinationVariants.stream()
                .filter(variant -> sameText(variant.getVariantSku(), sourceVariant.getVariantSku()))
                .findFirst();
    }

    private ItemUnit resolveTransferSourceUnit(Long branchId, Long itemId, Long unitId) {
        if (unitId == null) {
            throw new RuntimeException("Transfer unit is required");
        }

        return itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(itemId, unitId)
                .filter(unit -> branchId.equals(unit.getBranchId()))
                .orElseThrow(() -> new RuntimeException("Active transfer unit not found: " + unitId));
    }

    private ItemUnit ensureDestinationUnit(ItemUnit sourceUnit, Item destinationItem) {
        List<ItemUnit> destinationUnits = itemUnitRepository.findByItemIdAndIsActiveTrue(destinationItem.getItemId())
                .stream()
                .filter(unit -> destinationItem.getBranchId().equals(unit.getBranchId()))
                .toList();

        if (sourceUnit.getBarcode() != null && !sourceUnit.getBarcode().isBlank()) {
            java.util.Optional<ItemUnit> byBarcode = itemUnitRepository
                    .findByBranchIdAndBarcodeAndIsActiveTrue(destinationItem.getBranchId(), sourceUnit.getBarcode())
                    .filter(unit -> destinationItem.getItemId().equals(unit.getItemId()))
                    .filter(unit -> sameMultiplier(unit, sourceUnit));
            if (byBarcode.isPresent()) {
                return byBarcode.get();
            }
        }

        String sourceUnitName = resolveUnitName(sourceUnit);
        java.util.Optional<ItemUnit> byNameAndMultiplier = destinationUnits.stream()
                .filter(unit -> sameMultiplier(unit, sourceUnit))
                .filter(unit -> sameText(resolveUnitName(unit), sourceUnitName)
                        || sameText(unit.getUnitName(), sourceUnit.getUnitName()))
                .findFirst();
        if (byNameAndMultiplier.isPresent()) {
            return byNameAndMultiplier.get();
        }

        java.util.Optional<ItemUnit> inactiveMatch = itemUnitRepository.findByItemId(destinationItem.getItemId())
                .stream()
                .filter(unit -> destinationItem.getBranchId().equals(unit.getBranchId()))
                .filter(unit -> sameMultiplier(unit, sourceUnit))
                .filter(unit -> sameText(resolveUnitName(unit), sourceUnitName)
                        || sameText(unit.getUnitName(), sourceUnit.getUnitName()))
                .findFirst();
        if (inactiveMatch.isPresent()) {
            ItemUnit unit = inactiveMatch.get();
            unit.setIsActive(true);
            unit.setUpdatedAt(LocalDateTime.now());
            return itemUnitRepository.save(unit);
        }

        return copyDestinationUnit(sourceUnit, destinationItem);
    }

    private void copyDestinationUnits(Item sourceItem, Item destinationItem) {
        itemUnitRepository.findByItemId(sourceItem.getItemId())
                .forEach(sourceUnit -> copyDestinationUnit(sourceUnit, destinationItem));
    }

    private ItemUnit copyDestinationUnit(ItemUnit sourceUnit, Item destinationItem) {
        ItemUnit unit = new ItemUnit();
        unit.setBranchId(destinationItem.getBranchId());
        unit.setItemId(destinationItem.getItemId());
        unit.setMasterUnitId(null);
        unit.setUnitName(sourceUnit.getUnitName());
        unit.setMultiplierToBase(sourceUnit.getMultiplierToBase());
        unit.setBarcode(resolveCopyBarcode(sourceUnit.getBarcode(), destinationItem.getBranchId()));
        unit.setDefaultSellingPrice(sourceUnit.getDefaultSellingPrice());
        unit.setIsBaseUnit(sourceUnit.getIsBaseUnit());
        unit.setIsActive(true);
        unit.setCreatedAt(LocalDateTime.now());
        unit.setUpdatedAt(LocalDateTime.now());
        return itemUnitRepository.save(unit);
    }

    private void copyDestinationVariants(Item sourceItem, Item destinationItem) {
        itemVariantRepository.findByItemIdOrderByVariantIdAsc(sourceItem.getItemId())
                .forEach(sourceVariant -> copyDestinationVariant(sourceVariant, destinationItem));
    }

    private ItemVariant copyDestinationVariant(ItemVariant sourceVariant, Item destinationItem) {
        ItemVariant variant = new ItemVariant();
        variant.setItemId(destinationItem.getItemId());
        variant.setBranchId(destinationItem.getBranchId());
        variant.setSku(resolveCopyVariantSku(sourceVariant.getSku(), destinationItem.getBranchId()));
        variant.setVariantSku(generateCopiedVariantSku(destinationItem, sourceVariant));
        variant.setDefaultSellingPrice(sourceVariant.getDefaultSellingPrice());
        variant.setImage(sourceVariant.getImage());
        variant.setCombinationSignature(sourceVariant.getCombinationSignature());
        variant.setIsActive(true);
        variant.setCreatedAt(LocalDateTime.now());
        variant.setUpdatedAt(LocalDateTime.now());

        ItemVariant savedVariant = itemVariantRepository.save(variant);
        List<ItemVariantAttribute> copiedAttributes = itemVariantAttributeRepository
                .findByVariantIdOrderByAttributeNameAsc(sourceVariant.getVariantId())
                .stream()
                .map(attribute -> {
                    ItemVariantAttribute copy = new ItemVariantAttribute();
                    copy.setVariantId(savedVariant.getVariantId());
                    copy.setAttributeName(attribute.getAttributeName());
                    copy.setAttributeValue(attribute.getAttributeValue());
                    return copy;
                })
                .toList();
        itemVariantAttributeRepository.saveAll(copiedAttributes);
        return savedVariant;
    }

    private Long resolveMatchingCategoryId(Long sourceCategoryId, Long destinationBranchId) {
        if (sourceCategoryId == null) {
            return null;
        }
        return categoryRepository.findById(sourceCategoryId)
                .flatMap(sourceCategory -> categoryRepository.findByBranchId(destinationBranchId)
                        .stream()
                        .filter(destinationCategory -> sameText(destinationCategory.getName(), sourceCategory.getName()))
                        .filter(destinationCategory -> Boolean.TRUE.equals(destinationCategory.getIsActive()))
                        .findFirst())
                .map(Category::getCategoryId)
                .orElse(null);
    }

    private Long resolveMatchingBrandId(Long sourceBrandId, Long destinationBranchId) {
        if (sourceBrandId == null) {
            return null;
        }
        return brandRepository.findById(sourceBrandId)
                .flatMap(sourceBrand -> brandRepository.findByBranchId(destinationBranchId)
                        .stream()
                        .filter(destinationBrand -> sameText(destinationBrand.getName(), sourceBrand.getName()))
                        .filter(destinationBrand -> Boolean.TRUE.equals(destinationBrand.getIsActive()))
                        .findFirst())
                .map(Brand::getBrandId)
                .orElse(null);
    }

    private String resolveCopyBarcode(String barcode, Long destinationBranchId) {
        if (barcode == null || barcode.isBlank()) {
            return null;
        }
        return itemUnitRepository.existsByBranchIdAndBarcode(destinationBranchId, barcode) ? null : barcode;
    }

    private String resolveCopyVariantSku(String sku, Long destinationBranchId) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        boolean duplicate = itemRepository.existsByBranchIdAndSku(destinationBranchId, sku)
                || itemVariantRepository.existsByBranchIdAndSku(destinationBranchId, sku)
                || itemVariantRepository.existsByBranchIdAndVariantSku(destinationBranchId, sku);
        return duplicate ? null : sku;
    }

    private String generateCopiedVariantSku(Item destinationItem, ItemVariant sourceVariant) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String seed = destinationItem.getBranchId() + ":" + destinationItem.getItemId() + ":"
                    + sourceVariant.getCombinationSignature() + ":" + attempt;
            String sku = "V" + destinationItem.getItemId() + "-"
                    + UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString()
                    .substring(0, 8)
                    .toUpperCase();
            if (!itemVariantRepository.existsByBranchIdAndVariantSku(destinationItem.getBranchId(), sku)
                    && !itemVariantRepository.existsByBranchIdAndSku(destinationItem.getBranchId(), sku)
                    && !itemRepository.existsByBranchIdAndSku(destinationItem.getBranchId(), sku)) {
                return sku;
            }
        }
        return "V" + destinationItem.getItemId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Item findItemInBranchOrNull(Long branchId, Long itemId) {
        if (branchId == null || itemId == null) {
            return null;
        }
        return itemRepository.findByItemIdAndBranchId(itemId, branchId).orElse(null);
    }

    private Item resolveDestinationItemOrNull(Item sourceItem, Long toBranchId) {
        if (sourceItem == null || toBranchId == null) {
            return null;
        }
        return itemRepository.findByBranchIdAndSku(toBranchId, sourceItem.getSku()).orElse(null);
    }

    private ItemVariant findVariantOrNull(Long itemId, Long variantId) {
        if (itemId == null || variantId == null) {
            return null;
        }
        return itemVariantRepository.findByVariantIdAndItemId(variantId, itemId).orElse(null);
    }

    private ItemUnit findTransferUnitOrNull(Long itemId, Long unitId) {
        if (itemId == null || unitId == null) {
            return null;
        }
        return itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(itemId, unitId)
                .orElseGet(() -> itemUnitRepository.findById(unitId).orElse(null));
    }

    private boolean sameMultiplier(ItemUnit left, ItemUnit right) {
        if (left == null || right == null || left.getMultiplierToBase() == null || right.getMultiplierToBase() == null) {
            return false;
        }
        return left.getMultiplierToBase().compareTo(right.getMultiplierToBase()) == 0;
    }

    private boolean sameText(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
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

    @Override
    public Page<StockTransferResponseDto> getStockTransfersByBranch(Long branchId, Pageable pageable) {
        return stockTransferRepository.findByFromBranchIdOrToBranchId(branchId, branchId, pageable)
                .map(this::mapStockTransfer);
    }

    private StockTransferResponseDto mapStockTransfer(StockTransfer transfer) {
        List<StockTransferItemResponseDto> items = stockTransferItemRepository.findByTransferId(transfer.getTransferId())
                .stream()
                .map(i -> {
                    Item sourceItem = findItemInBranchOrNull(transfer.getFromBranchId(), i.getItemId());
                    Item destinationItem = resolveDestinationItemOrNull(sourceItem, transfer.getToBranchId());
                    ItemVariant sourceVariant = findVariantOrNull(i.getItemId(), i.getVariantId());
                    ItemVariant destinationVariant = findDestinationVariant(sourceVariant, destinationItem).orElse(null);
                    ItemUnit unit = findTransferUnitOrNull(i.getItemId(), i.getUnitId());

                    return StockTransferItemResponseDto.builder()
                            .transferItemId(i.getTransferItemId())
                            .transferId(i.getTransferId())
                            .itemId(i.getItemId())
                            .itemName(sourceItem != null ? sourceItem.getName() : null)
                            .itemSku(sourceItem != null ? sourceItem.getSku() : null)
                            .variantId(i.getVariantId())
                            .variantSku(resolveVariantSku(i.getVariantId()))
                            .variantLabel(resolveVariantLabel(i.getVariantId()))
                            .destinationItemId(destinationItem != null ? destinationItem.getItemId() : null)
                            .destinationVariantId(destinationVariant != null ? destinationVariant.getVariantId() : null)
                            .unitId(i.getUnitId())
                            .unitName(resolveUnitName(unit))
                            .unitQuantity(unit != null ? toResponseUnitQty(i.getQuantity(), unit) : i.getQuantity())
                            .internalBatchBarcode(i.getInternalBatchBarcode())
                            .quantity(i.getQuantity())
                            .build();
                })
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
            validateItemUnitAndVariant(dto.getBranchId(), itemDto.getItemId(), null, itemDto.getVariantId());

            ItemUnit baseUnit = itemUnitRepository.findByItemIdAndIsBaseUnitTrue(itemDto.getItemId())
                    .orElseThrow(() -> new RuntimeException("Base unit not found for item: " + itemDto.getItemId()));

            Stock stock = findStock(dto.getBranchId(), itemDto.getItemId(), itemDto.getVariantId())
                    .orElseGet(() -> {
                        Stock s = new Stock();
                        s.setBranchId(dto.getBranchId());
                        s.setItemId(itemDto.getItemId());
                        s.setVariantId(itemDto.getVariantId());
                        s.setUnitId(baseUnit.getUnitId());
                        return s;
                    });

            BigDecimal systemQty = getBatchQtyTotal(dto.getBranchId(), itemDto.getItemId(), itemDto.getVariantId(), StockQtyType.AVAILABLE);
            BigDecimal countedQty = nvlQty(itemDto.getCountedQty());
            BigDecimal diff = countedQty.subtract(systemQty);

            StockCountItem countItem = new StockCountItem();
            countItem.setStockCountId(savedCount.getStockCountId());
            countItem.setItemId(itemDto.getItemId());
            countItem.setVariantId(itemDto.getVariantId());
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
                        .variantId(i.getVariantId())
                        .variantSku(resolveVariantSku(i.getVariantId()))
                        .variantLabel(resolveVariantLabel(i.getVariantId()))
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

    @Override
    public Page<StockCountResponseDto> getStockCountsByBranch(Long branchId, Pageable pageable) {
        return stockCountRepository.findByBranchId(branchId, pageable)
                .map(c -> getStockCountById(c.getStockCountId()));
    }

    private void validateItemUnitAndVariant(Long branchId, Long itemId, Long unitId, Long variantId) {
        Item item = itemRepository.findByItemIdAndIsActiveTrue(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found or inactive: " + itemId));

        if (!item.getBranchId().equals(branchId)) {
            throw new RuntimeException("Item does not belong to branch: " + itemId);
        }

        if (unitId != null) {
            ItemUnit itemUnit = itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(itemId, unitId)
                    .orElseThrow(() -> new RuntimeException("Unit not found or inactive for itemId=" + itemId + ", unitId=" + unitId));

            if (!itemUnit.getBranchId().equals(branchId)) {
                throw new RuntimeException("Unit does not belong to branch for unitId=" + unitId);
            }
        }

        if (variantId != null) {
            ItemVariant variant = itemVariantRepository.findByVariantIdAndItemId(variantId, itemId)
                    .orElseThrow(() -> new RuntimeException("Variant not found for item: " + variantId));
            if (!variant.getBranchId().equals(branchId)) {
                throw new RuntimeException("Variant does not belong to branch: " + variantId);
            }
            if (!Boolean.TRUE.equals(variant.getIsActive())) {
                throw new RuntimeException("Variant is inactive: " + variantId);
            }
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
                .variantId(stock.getVariantId())
                .variantSku(resolveVariantSku(stock.getVariantId()))
                .variantLabel(resolveVariantLabel(stock.getVariantId()))
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
        List<StockBatch> batches = findBatches(stock.getBranchId(), stock.getItemId(), stock.getVariantId()).stream()
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
                .masterUnitId(unit.getMasterUnitId())
                .unitName(resolveUnitName(unit))
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
        SupplyProduct supplyProduct = batch.getSupplyProductId() != null && supplyProductRepository != null
                ? supplyProductRepository.findById(batch.getSupplyProductId()).orElse(null)
                : null;
        Supply supply = supplyProduct != null && supplyRepository != null
                ? supplyRepository.findById(supplyProduct.getSupplyId()).orElse(null)
                : null;

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
                .variantId(batch.getVariantId())
                .variantSku(resolveVariantSku(batch.getVariantId()))
                .variantLabel(resolveVariantLabel(batch.getVariantId()))
                .itemSku(item != null ? item.getSku() : null)
                .itemName(item != null ? item.getName() : null)
                .supplyProductId(batch.getSupplyProductId())
                .supplyId(supplyProduct != null ? supplyProduct.getSupplyId() : null)
                .supplierId(supply != null ? supply.getSupplierId() : null)
                .grnNo(supply != null ? supply.getGrnNo() : null)
                .unitId(batch.getUnitId())
                .masterUnitId(unit != null ? unit.getMasterUnitId() : null)
                .unitName(resolveUnitName(unit))
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

    private String resolveUnitName(ItemUnit unit) {
        if (unit == null) {
            return null;
        }

        if (unit.getMasterUnitId() != null) {
            return unitMasterRepository.findById(unit.getMasterUnitId())
                    .map(UnitMaster::getName)
                    .orElse(unit.getUnitName());
        }

        return unit.getUnitName();
    }

    private StockMovementResponseDto mapMovement(StockMovement movement) {
        return StockMovementResponseDto.builder()
                .movementId(movement.getMovementId())
                .branchId(movement.getBranchId())
                .movementType(movement.getMovementType())
                .itemId(movement.getItemId())
                .variantId(movement.getVariantId())
                .variantSku(resolveVariantSku(movement.getVariantId()))
                .variantLabel(resolveVariantLabel(movement.getVariantId()))
                .unitId(movement.getUnitId())
                .internalBatchBarcode(movement.getInternalBatchBarcode())
                .quantity(movement.getQuantity())
                .unitCost(movement.getUnitCost())
                .unitPrice(movement.getUnitPrice())
                .refTable(movement.getRefTable())
                .refId(movement.getRefId())
                .note(movement.getNote())
                .createdBy(movement.getCreatedBy())
                .createdByName(resolveUserName(movement.getCreatedBy()))
                .createdAt(movement.getCreatedAt())
                .build();
    }

    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(user -> user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName()
                        : "User #" + userId)
                .orElse("User #" + userId);
    }

    private BigDecimal nvlQty(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal getBatchQtyTotal(Long branchId, Long itemId, Long variantId, StockQtyType type) {
        return findBatches(branchId, itemId, variantId)
                .stream()
                .map(batch -> switch (type) {
                    case AVAILABLE -> nvlQty(batch.getAvailableQty() != null ? batch.getAvailableQty() : batch.getQtyRemaining());
                    case DAMAGED -> nvlQty(batch.getDamagedQty());
                    case EXPIRED -> nvlQty(batch.getExpiredQty());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private java.util.Optional<Stock> findStock(Long branchId, Long itemId, Long variantId) {
        return stockRepository.findByBranchIdAndItemIdAndVariantId(branchId, itemId, variantId);
    }

    private List<StockBatch> findBatches(Long branchId, Long itemId, Long variantId) {
        return stockBatchRepository.findByBranchIdAndItemIdAndVariantIdOrderByCreatedAtDesc(branchId, itemId, variantId);
    }

    private Long resolveBatchVariantId(Long branchId, Long itemId, Long requestedVariantId, String batchBarcode) {
        if (batchBarcode == null || batchBarcode.isBlank()) {
            return requestedVariantId;
        }

        return stockBatchRepository.findByBranchIdAndInternalBatchBarcode(branchId, batchBarcode)
                .filter(batch -> itemId.equals(batch.getItemId()))
                .map(batch -> batch.getVariantId() != null ? batch.getVariantId() : requestedVariantId)
                .orElse(requestedVariantId);
    }

    private boolean sameVariant(Long left, Long right) {
        return java.util.Objects.equals(left, right);
    }

    private String resolveVariantSku(Long variantId) {
        if (variantId == null) {
            return null;
        }
        return itemVariantRepository.findById(variantId)
                .map(variant -> variant.getSku() != null ? variant.getSku() : variant.getVariantSku())
                .orElse(null);
    }

    private String resolveVariantLabel(Long variantId) {
        if (variantId == null) {
            return null;
        }
        String label = itemVariantAttributeRepository.findByVariantIdOrderByAttributeNameAsc(variantId)
                .stream()
                .map(attribute -> attribute.getAttributeName() + ": " + attribute.getAttributeValue())
                .reduce((left, right) -> left + " / " + right)
                .orElse(null);
        return label != null && !label.isBlank() ? label : resolveVariantSku(variantId);
    }

    private enum StockQtyType {
        AVAILABLE,
        DAMAGED,
        EXPIRED
    }
}
