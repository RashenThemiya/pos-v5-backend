package com.pos.system.service;

import com.pos.system.dto.supplier.*;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.stock.Stock;
import com.pos.system.model.stock.StockBatch;
import com.pos.system.model.stock.StockMovement;
import com.pos.system.model.supplier.*;
import com.pos.system.repository.*;
import com.pos.system.service.UnitConversionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SupplierManagementServiceImpl implements SupplierManagementService {

    private final SupplierRepository supplierRepository;
    private final SupplierItemRepository supplierItemRepository;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    private final SupplyRepository supplyRepository;
    private final SupplyProductRepository supplyProductRepository;

    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierBalanceTransactionRepository supplierBalanceTransactionRepository;

    private final StockRepository stockRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockMovementRepository stockMovementRepository;

    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final UnitConversionService unitConversionService;

    // =========================
    // SUPPLIER
    // =========================

    @Override
    public SupplierResponseDto createSupplier(SupplierRequestDto dto) {
        supplierRepository.findByBranchIdAndName(dto.getBranchId(), dto.getName())
                .ifPresent(x -> {
                    throw new RuntimeException("Supplier already exists in this branch");
                });

        Supplier supplier = new Supplier();
        supplier.setBranchId(dto.getBranchId());
        supplier.setAuthId(dto.getAuthId());
        supplier.setName(dto.getName());
        supplier.setContactPerson(dto.getContactPerson());
        supplier.setPhone(dto.getPhone());
        supplier.setEmail(dto.getEmail());
        supplier.setAddress(dto.getAddress());
        supplier.setBalance(BigDecimal.ZERO);
        supplier.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        supplier.setCreatedAt(LocalDateTime.now());
        supplier.setUpdatedAt(LocalDateTime.now());

        return mapSupplier(supplierRepository.save(supplier));
    }

    @Override
    public SupplierResponseDto updateSupplier(Long supplierId, SupplierRequestDto dto) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        supplier.setAuthId(dto.getAuthId());
        supplier.setName(dto.getName());
        supplier.setContactPerson(dto.getContactPerson());
        supplier.setPhone(dto.getPhone());
        supplier.setEmail(dto.getEmail());
        supplier.setAddress(dto.getAddress());

        if (dto.getIsActive() != null) {
            supplier.setIsActive(dto.getIsActive());
        }

        supplier.setUpdatedAt(LocalDateTime.now());

        return mapSupplier(supplierRepository.save(supplier));
    }

    @Override
    public SupplierResponseDto getSupplierById(Long supplierId) {
        return mapSupplier(
                supplierRepository.findById(supplierId)
                        .orElseThrow(() -> new RuntimeException("Supplier not found"))
        );
    }

    @Override
    public List<SupplierResponseDto> getSuppliersByBranch(Long branchId) {
        return supplierRepository.findByBranchId(branchId)
                .stream()
                .map(this::mapSupplier)
                .toList();
    }

    // =========================
    // SUPPLIER ITEMS
    // =========================

    @Override
    public SupplierItemResponseDto addSupplierItem(SupplierItemRequestDto dto) {

        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        if (!supplier.getBranchId().equals(dto.getBranchId())) {
            throw new RuntimeException("Supplier does not belong to this branch");
        }

        validateItemAndUnit(dto.getBranchId(), dto.getItemId(), dto.getUnitId());

        supplierItemRepository
                .findByBranchIdAndSupplierIdAndItemIdAndUnitId(
                        dto.getBranchId(),
                        dto.getSupplierId(),
                        dto.getItemId(),
                        dto.getUnitId()
                )
                .ifPresent(x -> {
                    throw new RuntimeException("Supplier already supplies this item with this unit");
                });

        SupplierItem supplierItem = new SupplierItem();
        supplierItem.setBranchId(dto.getBranchId());
        supplierItem.setSupplierId(dto.getSupplierId());
        supplierItem.setItemId(dto.getItemId());
        supplierItem.setUnitId(dto.getUnitId());
        supplierItem.setLastPurchaseCost(nvlMoney(dto.getLastPurchaseCost()));
        supplierItem.setDefaultCostPrice(nvlMoney(dto.getDefaultCostPrice()));
        supplierItem.setIsPreferred(dto.getIsPreferred() != null ? dto.getIsPreferred() : false);
        supplierItem.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        supplierItem.setCreatedAt(LocalDateTime.now());
        supplierItem.setUpdatedAt(LocalDateTime.now());

        return mapSupplierItem(supplierItemRepository.save(supplierItem));
    }

    @Override
    public SupplierItemResponseDto updateSupplierItem(Long supplierItemId, SupplierItemRequestDto dto) {

        SupplierItem supplierItem = supplierItemRepository.findById(supplierItemId)
                .orElseThrow(() -> new RuntimeException("Supplier item not found"));

        Supplier supplier = supplierRepository.findById(supplierItem.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        if (!supplier.getBranchId().equals(dto.getBranchId())) {
            throw new RuntimeException("Supplier does not belong to this branch");
        }

        validateItemAndUnit(dto.getBranchId(), dto.getItemId(), dto.getUnitId());

        supplierItem.setBranchId(dto.getBranchId());
        supplierItem.setSupplierId(dto.getSupplierId());
        supplierItem.setItemId(dto.getItemId());
        supplierItem.setUnitId(dto.getUnitId());
        supplierItem.setLastPurchaseCost(nvlMoney(dto.getLastPurchaseCost()));
        supplierItem.setDefaultCostPrice(nvlMoney(dto.getDefaultCostPrice()));

        if (dto.getIsPreferred() != null) {
            supplierItem.setIsPreferred(dto.getIsPreferred());
        }

        if (dto.getIsActive() != null) {
            supplierItem.setIsActive(dto.getIsActive());
        }

        supplierItem.setUpdatedAt(LocalDateTime.now());

        return mapSupplierItem(supplierItemRepository.save(supplierItem));
    }

    @Override
    public List<SupplierItemResponseDto> getSupplierItemsBySupplier(Long branchId, Long supplierId) {
        return supplierItemRepository
                .findByBranchIdAndSupplierIdAndIsActiveTrue(branchId, supplierId)
                .stream()
                .map(this::mapSupplierItem)
                .toList();
    }

    @Override
    public List<SupplierItemResponseDto> getSuppliersByItem(Long branchId, Long itemId) {
        return supplierItemRepository
                .findByBranchIdAndItemIdAndIsActiveTrue(branchId, itemId)
                .stream()
                .map(this::mapSupplierItem)
                .toList();
    }

    @Override
    public void deleteSupplierItem(Long supplierItemId) {
        SupplierItem supplierItem = supplierItemRepository.findById(supplierItemId)
                .orElseThrow(() -> new RuntimeException("Supplier item not found"));

        supplierItem.setIsActive(false);
        supplierItem.setUpdatedAt(LocalDateTime.now());

        supplierItemRepository.save(supplierItem);
    }

    // =========================
    // PURCHASE ORDER
    // =========================

    @Override
    public PurchaseOrderResponseDto createPurchaseOrder(PurchaseOrderRequestDto dto) {
        purchaseOrderRepository.findByPoNo(dto.getPoNo())
                .ifPresent(x -> {
                    throw new RuntimeException("PO number already exists");
                });

        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        if (!supplier.getBranchId().equals(dto.getBranchId())) {
            throw new RuntimeException("Supplier does not belong to this branch");
        }

        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new RuntimeException("PO items cannot be empty");
        }

        validateDuplicatePoItems(dto.getItems());

        PurchaseOrder po = new PurchaseOrder();
        po.setBranchId(dto.getBranchId());
        po.setSupplierId(dto.getSupplierId());
        po.setPoNo(dto.getPoNo());
        po.setStatus(dto.getStatus() != null ? dto.getStatus() : "PENDING");
        po.setExpectedDate(dto.getExpectedDate());
        po.setCreatedBy(dto.getCreatedBy());
        po.setCreatedAt(LocalDateTime.now());
        po.setNote(dto.getNote());

        PurchaseOrder savedPo = purchaseOrderRepository.save(po);

        for (PurchaseOrderItemRequestDto itemDto : dto.getItems()) {
            validateItemAndUnit(dto.getBranchId(), itemDto.getItemId(), itemDto.getUnitId());

            validateSupplierProvidesItem(
                    dto.getBranchId(),
                    dto.getSupplierId(),
                    itemDto.getItemId(),
                    itemDto.getUnitId()
            );

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPoId(savedPo.getPoId());
            item.setItemId(itemDto.getItemId());
            item.setUnitId(itemDto.getUnitId());
            item.setOrderedQty(nvlQty(itemDto.getOrderedQty()));
            item.setReceivedQty(BigDecimal.ZERO);
            item.setUnitCostEst(itemDto.getUnitCostEst());

            purchaseOrderItemRepository.save(item);
        }

        return getPurchaseOrderById(savedPo.getPoId());
    }

    @Override
    public PurchaseOrderResponseDto getPurchaseOrderById(Long poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));

        List<PurchaseOrderItemResponseDto> items = purchaseOrderItemRepository.findByPoId(poId)
                .stream()
                .map(item -> PurchaseOrderItemResponseDto.builder()
                        .poItemId(item.getPoItemId())
                        .poId(item.getPoId())
                        .itemId(item.getItemId())
                        .unitId(item.getUnitId())
                        .orderedQty(item.getOrderedQty())
                        .receivedQty(item.getReceivedQty())
                        .unitCostEst(item.getUnitCostEst())
                        .build())
                .toList();

        return PurchaseOrderResponseDto.builder()
                .poId(po.getPoId())
                .branchId(po.getBranchId())
                .supplierId(po.getSupplierId())
                .poNo(po.getPoNo())
                .status(po.getStatus())
                .expectedDate(po.getExpectedDate())
                .createdBy(po.getCreatedBy())
                .createdAt(po.getCreatedAt())
                .note(po.getNote())
                .items(items)
                .build();
    }

    @Override
    public List<PurchaseOrderResponseDto> getPurchaseOrdersByBranch(Long branchId) {
        return purchaseOrderRepository.findByBranchId(branchId)
                .stream()
                .map(po -> getPurchaseOrderById(po.getPoId()))
                .toList();
    }

    // =========================
    // SUPPLY / GRN
    // =========================

    @Override
    public SupplyResponseDto createSupply(SupplyRequestDto dto) {
        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        if (!supplier.getBranchId().equals(dto.getBranchId())) {
            throw new RuntimeException("Supplier does not belong to this branch");
        }

        if (dto.getGrnNo() != null && !dto.getGrnNo().isBlank()) {
            supplyRepository.findByGrnNo(dto.getGrnNo())
                    .ifPresent(x -> {
                        throw new RuntimeException("GRN number already exists");
                    });
        }

        if (dto.getProducts() == null || dto.getProducts().isEmpty()) {
            throw new RuntimeException("Supply products cannot be empty");
        }

        validateDuplicateSupplyLines(dto.getProducts());

        String status = dto.getStatus() != null ? dto.getStatus() : "COMPLETED";

        Supply supply = new Supply();
        supply.setBranchId(dto.getBranchId());
        supply.setSupplierId(dto.getSupplierId());
        supply.setPoId(dto.getPoId());
        supply.setGrnNo(dto.getGrnNo());
        supply.setInvoiceNo(dto.getInvoiceNo());
        supply.setSubtotal(dto.getSubtotal());
        supply.setDiscount(dto.getDiscount());
        supply.setTaxAmount(nvlMoney(dto.getTaxAmount()));
        supply.setRounding(nvlMoney(dto.getRounding()));
        supply.setTotal(dto.getTotal());
        supply.setPaidAmount(nvlMoney(dto.getPaidAmount()));
        supply.setPaymentMethod(dto.getPaymentMethod());
        supply.setStatus(status);
        supply.setSupplyDate(dto.getSupplyDate() != null ? dto.getSupplyDate() : LocalDateTime.now());
        supply.setReceivedBy(dto.getReceivedBy());
        supply.setNotes(dto.getNotes());

        Supply savedSupply = supplyRepository.save(supply);

        BigDecimal calculatedTotal = BigDecimal.ZERO;

        for (SupplyProductRequestDto p : dto.getProducts()) {
            validateItemAndUnit(dto.getBranchId(), p.getItemId(), p.getUnitId());

            validateSupplierProvidesItem(
                    dto.getBranchId(),
                    dto.getSupplierId(),
                    p.getItemId(),
                    p.getUnitId()
            );

            BigDecimal qtyReceived = nvlQty(p.getQuantityReceived());
            BigDecimal qtyBase = unitConversionService.toBaseQty(p.getItemId(), p.getUnitId(), qtyReceived);

            SupplyProduct product = new SupplyProduct();
            product.setSupplyId(savedSupply.getSupplyId());
            product.setItemId(p.getItemId());
            product.setUnitId(p.getUnitId());
            product.setBatchNo(p.getBatchNo());
            product.setSupplierBatchBarcode(p.getSupplierBatchBarcode());
            product.setProductBarcode(resolveProductBarcode(dto.getBranchId(), p.getItemId(), p.getUnitId(), p.getProductBarcode()));
            product.setInternalBatchBarcode(generateUniqueInternalBatchBarcode(dto.getBranchId(), p.getItemId()));
            product.setCostPrice(nvlMoney(p.getCostPrice()));
            product.setSellingPrice(nvlMoney(p.getSellingPrice()));
            product.setQuantityReceived(qtyReceived);
            product.setQuantityReceivedBase(qtyBase);
            product.setQtyRemaining(qtyBase);
            product.setQtyDamaged(nvlQty(p.getQtyDamaged()));
            product.setQtyExpired(nvlQty(p.getQtyExpired()));
            product.setExpiryDate(p.getExpiryDate());
            product.setLineTotal(p.getLineTotal());
            product.setCreatedAt(LocalDateTime.now());

            SupplyProduct savedProduct = supplyProductRepository.save(product);

            if (p.getLineTotal() != null) {
                calculatedTotal = calculatedTotal.add(p.getLineTotal());
            }

            if ("COMPLETED".equalsIgnoreCase(status)) {
                addPurchasedStock(savedSupply, savedProduct);
                updatePoReceivedQtyIfNeeded(savedSupply.getPoId(), savedProduct);
                updateSupplierItemLastPurchaseCost(
                        dto.getBranchId(),
                        dto.getSupplierId(),
                        p.getItemId(),
                        p.getUnitId(),
                        savedProduct.getCostPrice()
                );
            }
        }

        BigDecimal finalTotal = dto.getTotal() != null ? dto.getTotal() : calculatedTotal;
        BigDecimal paidAmount = nvlMoney(dto.getPaidAmount());
        BigDecimal balanceIncrease = finalTotal.subtract(paidAmount);

        supply.setTotal(finalTotal);
        supplyRepository.save(supply);

        if ("COMPLETED".equalsIgnoreCase(status) && balanceIncrease.compareTo(BigDecimal.ZERO) > 0) {
            supplier.setBalance(nvlMoney(supplier.getBalance()).add(balanceIncrease));
            supplier.setUpdatedAt(LocalDateTime.now());
            supplierRepository.save(supplier);

            SupplierBalanceTransaction txn = new SupplierBalanceTransaction();
            txn.setBranchId(dto.getBranchId());
            txn.setSupplierId(dto.getSupplierId());
            txn.setType("SUPPLY_CREDIT");
            txn.setAmount(balanceIncrease);
            txn.setRefTable("supplies");
            txn.setRefId(savedSupply.getSupplyId());
            txn.setNote("Balance added from completed supply");
            txn.setCreatedBy(dto.getReceivedBy());
            txn.setCreatedAt(LocalDateTime.now());
            supplierBalanceTransactionRepository.save(txn);
        }

        return getSupplyById(savedSupply.getSupplyId());
    }

    @Override
    public SupplyResponseDto getSupplyById(Long supplyId) {
        Supply supply = supplyRepository.findById(supplyId)
                .orElseThrow(() -> new RuntimeException("Supply not found"));

        List<SupplyProductResponseDto> products = supplyProductRepository.findBySupplyId(supplyId)
                .stream()
                .map(product -> SupplyProductResponseDto.builder()
                        .supplyProductId(product.getSupplyProductId())
                        .supplyId(product.getSupplyId())
                        .itemId(product.getItemId())
                        .unitId(product.getUnitId())
                        .batchNo(product.getBatchNo())
                        .supplierBatchBarcode(product.getSupplierBatchBarcode())
                        .productBarcode(product.getProductBarcode())
                        .internalBatchBarcode(product.getInternalBatchBarcode())
                        .costPrice(product.getCostPrice())
                        .sellingPrice(product.getSellingPrice())
                        .quantityReceived(product.getQuantityReceived())
                        .quantityReceivedBase(product.getQuantityReceivedBase())
                        .qtyRemaining(product.getQtyRemaining())
                        .qtyDamaged(product.getQtyDamaged())
                        .qtyExpired(product.getQtyExpired())
                        .expiryDate(product.getExpiryDate())
                        .lineTotal(product.getLineTotal())
                        .createdAt(product.getCreatedAt())
                        .build())
                .toList();

        return SupplyResponseDto.builder()
                .supplyId(supply.getSupplyId())
                .branchId(supply.getBranchId())
                .supplierId(supply.getSupplierId())
                .poId(supply.getPoId())
                .grnNo(supply.getGrnNo())
                .invoiceNo(supply.getInvoiceNo())
                .subtotal(supply.getSubtotal())
                .discount(supply.getDiscount())
                .taxAmount(supply.getTaxAmount())
                .rounding(supply.getRounding())
                .total(supply.getTotal())
                .paidAmount(supply.getPaidAmount())
                .paymentMethod(supply.getPaymentMethod())
                .status(supply.getStatus())
                .supplyDate(supply.getSupplyDate())
                .receivedBy(supply.getReceivedBy())
                .notes(supply.getNotes())
                .products(products)
                .build();
    }

    @Override
    public List<SupplyResponseDto> getSuppliesByBranch(Long branchId) {
        return supplyRepository.findByBranchId(branchId)
                .stream()
                .map(s -> getSupplyById(s.getSupplyId()))
                .toList();
    }

    // =========================
    // SUPPLIER PAYMENT
    // =========================

    @Override
    public SupplierPaymentResponseDto createSupplierPayment(SupplierPaymentRequestDto dto) {
        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        if (!supplier.getBranchId().equals(dto.getBranchId())) {
            throw new RuntimeException("Supplier does not belong to this branch");
        }

        SupplierPayment payment = new SupplierPayment();
        payment.setBranchId(dto.getBranchId());
        payment.setSupplierId(dto.getSupplierId());
        payment.setSupplyId(dto.getSupplyId());
        payment.setAmount(nvlMoney(dto.getAmount()));
        payment.setPaymentMethod(dto.getPaymentMethod());
        payment.setPaymentDate(dto.getPaymentDate() != null ? dto.getPaymentDate() : LocalDateTime.now());
        payment.setPaidBy(dto.getPaidBy());
        payment.setCashSessionId(dto.getCashSessionId());
        payment.setReferenceNo(dto.getReferenceNo());
        payment.setNote(dto.getNote());

        SupplierPayment savedPayment = supplierPaymentRepository.save(payment);

        supplier.setBalance(nvlMoney(supplier.getBalance()).subtract(payment.getAmount()));
        supplier.setUpdatedAt(LocalDateTime.now());
        supplierRepository.save(supplier);

        SupplierBalanceTransaction txn = new SupplierBalanceTransaction();
        txn.setBranchId(dto.getBranchId());
        txn.setSupplierId(dto.getSupplierId());
        txn.setType("PAYMENT");
        txn.setAmount(payment.getAmount());
        txn.setRefTable("supplier_payments");
        txn.setRefId(savedPayment.getSupplierPaymentId());
        txn.setNote("Supplier payment recorded");
        txn.setCreatedBy(dto.getPaidBy());
        txn.setCreatedAt(LocalDateTime.now());
        supplierBalanceTransactionRepository.save(txn);

        return SupplierPaymentResponseDto.builder()
                .supplierPaymentId(savedPayment.getSupplierPaymentId())
                .branchId(savedPayment.getBranchId())
                .supplierId(savedPayment.getSupplierId())
                .supplyId(savedPayment.getSupplyId())
                .amount(savedPayment.getAmount())
                .paymentMethod(savedPayment.getPaymentMethod())
                .paymentDate(savedPayment.getPaymentDate())
                .paidBy(savedPayment.getPaidBy())
                .cashSessionId(savedPayment.getCashSessionId())
                .referenceNo(savedPayment.getReferenceNo())
                .note(savedPayment.getNote())
                .build();
    }

    // =========================
    // STOCK HELPERS
    // =========================

    private void addPurchasedStock(Supply supply, SupplyProduct product) {
        Stock stock = stockRepository.findByBranchIdAndItemId(supply.getBranchId(), product.getItemId())
                .orElseGet(() -> {
                    Stock s = new Stock();
                    s.setBranchId(supply.getBranchId());
                    s.setItemId(product.getItemId());
                    s.setAvailableQty(BigDecimal.ZERO);
                    s.setDamagedQty(BigDecimal.ZERO);
                    s.setExpiredQty(BigDecimal.ZERO);
                    return s;
                });

        stock.setAvailableQty(nvlQty(stock.getAvailableQty()).add(nvlQty(product.getQuantityReceivedBase())));
        stock.setDamagedQty(nvlQty(stock.getDamagedQty()).add(nvlQty(product.getQtyDamaged())));
        stock.setExpiredQty(nvlQty(stock.getExpiredQty()).add(nvlQty(product.getQtyExpired())));
        stock.setLastUpdated(LocalDateTime.now());
        stockRepository.save(stock);

        StockBatch batch = new StockBatch();
        batch.setBranchId(supply.getBranchId());
        batch.setItemId(product.getItemId());
        batch.setSupplyProductId(product.getSupplyProductId());
        batch.setUnitId(product.getUnitId());
        batch.setReceivedQty(product.getQuantityReceived());
        batch.setReceivedBaseQty(product.getQuantityReceivedBase());
        batch.setQtyRemaining(product.getQtyRemaining());
        batch.setInternalBatchBarcode(product.getInternalBatchBarcode());
        batch.setBatchNo(product.getBatchNo());
        batch.setSupplierBatchBarcode(product.getSupplierBatchBarcode());
        batch.setExpiryDate(product.getExpiryDate());
        batch.setCostPrice(product.getCostPrice());
        batch.setSellingPrice(product.getSellingPrice());
        batch.setCreatedAt(LocalDateTime.now());

        try {
            stockBatchRepository.save(batch);
        } catch (DataIntegrityViolationException ex) {
            throw new RuntimeException("Duplicate internal batch barcode detected while saving stock batch");
        }

        StockMovement movement = new StockMovement();
        movement.setBranchId(supply.getBranchId());
        movement.setMovementType("PURCHASE_IN");
        movement.setItemId(product.getItemId());
        movement.setUnitId(product.getUnitId());
        movement.setInternalBatchBarcode(product.getInternalBatchBarcode());
        movement.setQuantity(product.getQuantityReceivedBase());
        movement.setUnitCost(product.getCostPrice());
        movement.setUnitPrice(product.getSellingPrice());
        movement.setRefTable("supplies");
        movement.setRefId(supply.getSupplyId());
        movement.setNote("Stock added from completed supply");
        movement.setCreatedBy(supply.getReceivedBy());
        movement.setCreatedAt(LocalDateTime.now());

        stockMovementRepository.save(movement);
    }

    private void updatePoReceivedQtyIfNeeded(Long poId, SupplyProduct product) {
        if (poId == null) return;

        PurchaseOrderItem poItem = purchaseOrderItemRepository
                .findByPoIdAndItemIdAndUnitId(poId, product.getItemId(), product.getUnitId())
                .orElse(null);

        if (poItem != null) {
            poItem.setReceivedQty(nvlQty(poItem.getReceivedQty()).add(nvlQty(product.getQuantityReceived())));
            purchaseOrderItemRepository.save(poItem);
        }
    }

    // =========================
    // VALIDATION HELPERS
    // =========================

    private void validateItemAndUnit(Long branchId, Long itemId, Long unitId) {
        Item item = itemRepository.findByItemIdAndIsActiveTrue(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found or inactive: " + itemId));

        if (!item.getBranchId().equals(branchId)) {
            throw new RuntimeException("Item does not belong to branch: " + itemId);
        }

        ItemUnit itemUnit = itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(itemId, unitId)
                .orElseThrow(() -> new RuntimeException(
                        "Unit not found or inactive for itemId=" + itemId + ", unitId=" + unitId
                ));

        if (!itemUnit.getBranchId().equals(branchId)) {
            throw new RuntimeException("Unit does not belong to branch for unitId=" + unitId);
        }
    }

    private void validateSupplierProvidesItem(Long branchId, Long supplierId, Long itemId, Long unitId) {
        SupplierItem supplierItem = supplierItemRepository
                .findByBranchIdAndSupplierIdAndItemIdAndUnitId(branchId, supplierId, itemId, unitId)
                .orElseThrow(() -> new RuntimeException(
                        "Supplier does not supply itemId=" + itemId + " with unitId=" + unitId
                ));

        if (!Boolean.TRUE.equals(supplierItem.getIsActive())) {
            throw new RuntimeException(
                    "Supplier item mapping is inactive for itemId=" + itemId + " with unitId=" + unitId
            );
        }
    }

    private void updateSupplierItemLastPurchaseCost(
            Long branchId,
            Long supplierId,
            Long itemId,
            Long unitId,
            BigDecimal costPrice
    ) {
        supplierItemRepository
                .findByBranchIdAndSupplierIdAndItemIdAndUnitId(branchId, supplierId, itemId, unitId)
                .ifPresent(si -> {
                    si.setLastPurchaseCost(nvlMoney(costPrice));
                    si.setUpdatedAt(LocalDateTime.now());
                    supplierItemRepository.save(si);
                });
    }

    private String resolveProductBarcode(Long branchId, Long itemId, Long unitId, String requestBarcode) {
        if (requestBarcode != null && !requestBarcode.isBlank()) {
            return requestBarcode.trim();
        }

        return itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(itemId, unitId)
                .map(ItemUnit::getBarcode)
                .orElse(null);
    }

    private void validateDuplicatePoItems(List<PurchaseOrderItemRequestDto> items) {
        Set<String> keys = new HashSet<>();

        for (PurchaseOrderItemRequestDto item : items) {
            String key = item.getItemId() + "-" + item.getUnitId();

            if (!keys.add(key)) {
                throw new RuntimeException("Duplicate PO item found for itemId/unitId: " + key);
            }
        }
    }

    private void validateDuplicateSupplyLines(List<SupplyProductRequestDto> products) {
        Set<String> keys = new HashSet<>();

        for (SupplyProductRequestDto p : products) {
            String key = p.getItemId()
                    + "-"
                    + p.getUnitId()
                    + "-"
                    + safe(p.getBatchNo())
                    + "-"
                    + safe(p.getSupplierBatchBarcode());

            if (!keys.add(key)) {
                throw new RuntimeException("Duplicate supply line found: " + key);
            }
        }
    }

    private String generateUniqueInternalBatchBarcode(Long branchId, Long itemId) {
        for (int i = 0; i < 10; i++) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            String code = "BT-"
                    + branchId
                    + "-"
                    + itemId
                    + "-"
                    + timestamp
                    + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            boolean existsInSupplyProduct = supplyProductRepository.findByInternalBatchBarcode(code).isPresent();
            boolean existsInStockBatch = stockBatchRepository.findByBranchIdAndInternalBatchBarcode(branchId, code).isPresent();

            if (!existsInSupplyProduct && !existsInStockBatch) {
                return code;
            }
        }

        throw new RuntimeException("Failed to generate unique internal batch barcode");
    }

    // =========================
    // MAPPERS
    // =========================

    private SupplierResponseDto mapSupplier(Supplier supplier) {
        return SupplierResponseDto.builder()
                .supplierId(supplier.getSupplierId())
                .branchId(supplier.getBranchId())
                .authId(supplier.getAuthId())
                .name(supplier.getName())
                .contactPerson(supplier.getContactPerson())
                .phone(supplier.getPhone())
                .email(supplier.getEmail())
                .address(supplier.getAddress())
                .balance(supplier.getBalance())
                .isActive(supplier.getIsActive())
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }

    private SupplierItemResponseDto mapSupplierItem(SupplierItem supplierItem) {
        Item item = itemRepository.findById(supplierItem.getItemId()).orElse(null);
        Supplier supplier = supplierRepository.findById(supplierItem.getSupplierId()).orElse(null);

        return SupplierItemResponseDto.builder()
                .supplierItemId(supplierItem.getSupplierItemId())
                .branchId(supplierItem.getBranchId())

                .supplierId(supplierItem.getSupplierId())
                .supplierName(supplier != null ? supplier.getName() : null)

                .itemId(supplierItem.getItemId())
                .itemName(item != null ? item.getName() : null)
                .sku(item != null ? item.getSku() : null)

                .unitId(supplierItem.getUnitId())
                .lastPurchaseCost(supplierItem.getLastPurchaseCost())
                .defaultCostPrice(supplierItem.getDefaultCostPrice())

                .isPreferred(supplierItem.getIsPreferred())
                .isActive(supplierItem.getIsActive())

                .createdAt(supplierItem.getCreatedAt())
                .updatedAt(supplierItem.getUpdatedAt())
                .build();
    }

    // =========================
    // NULL HELPERS
    // =========================

    private BigDecimal nvlQty(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal nvlMoney(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}