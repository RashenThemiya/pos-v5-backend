package com.pos.system.service;

import com.pos.system.dto.supplier.*;
import com.pos.system.model.cash.CashSession;
import com.pos.system.model.cash.CashSessionTransaction;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.stock.Stock;
import com.pos.system.model.stock.StockBatch;
import com.pos.system.model.stock.StockMovement;
import com.pos.system.model.supplier.*;
import com.pos.system.repository.*;
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

    private static final Long SYSTEM_USER_ID = 1L;

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

    private final CashSessionRepository cashSessionRepository;
    private final CashSessionTransactionRepository cashSessionTransactionRepository;

    private final PurchaseReturnRepository purchaseReturnRepository;
private final PurchaseReturnItemRepository purchaseReturnItemRepository;

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
        if (dto.getPoNo() != null && !dto.getPoNo().isBlank()) {
            purchaseOrderRepository.findByPoNo(dto.getPoNo())
                    .ifPresent(x -> {
                        throw new RuntimeException("PO number already exists");
                    });
        }

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
        po.setPoNo(resolvePoNo(dto.getPoNo()));
        po.setStatus(dto.getStatus() != null ? dto.getStatus() : "PENDING");
        po.setExpectedDate(dto.getExpectedDate());
        po.setCreatedBy(resolveUserId(dto.getCreatedBy()));
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
            item.setUnitCostEst(nvlMoney(itemDto.getUnitCostEst()));

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

        Long receivedBy = resolveUserId(dto.getReceivedBy());
        String status = dto.getStatus() != null ? dto.getStatus() : "COMPLETED";

        Supply supply = new Supply();
        supply.setBranchId(dto.getBranchId());
        supply.setSupplierId(dto.getSupplierId());
        supply.setPoId(dto.getPoId());
        supply.setGrnNo(resolveGrnNo(dto.getGrnNo()));
        supply.setInvoiceNo(dto.getInvoiceNo());
        supply.setSubtotal(BigDecimal.ZERO);
        supply.setDiscount(nvlMoney(dto.getDiscount()));
        supply.setTaxAmount(nvlMoney(dto.getTaxAmount()));
        supply.setRounding(nvlMoney(dto.getRounding()));
        supply.setTotal(BigDecimal.ZERO);
        supply.setPaidAmount(nvlMoney(dto.getPaidAmount()));
        supply.setPayableAmount(BigDecimal.ZERO);
        supply.setBalanceAmount(BigDecimal.ZERO);
        supply.setPaymentStatus("UNPAID");
        supply.setPaymentMethod(dto.getPaymentMethod() != null ? dto.getPaymentMethod() : "CREDIT");
        supply.setStatus(status);
        supply.setSupplyDate(dto.getSupplyDate() != null ? dto.getSupplyDate() : LocalDateTime.now());
        supply.setReceivedBy(receivedBy);
        supply.setNotes(dto.getNotes());

        Supply savedSupply = supplyRepository.save(supply);

        BigDecimal calculatedSubtotal = BigDecimal.ZERO;

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
            BigDecimal costPrice = nvlMoney(p.getCostPrice());
            BigDecimal lineTotal = p.getLineTotal() != null ? p.getLineTotal() : costPrice.multiply(qtyReceived);

            SupplyProduct product = new SupplyProduct();
            product.setSupplyId(savedSupply.getSupplyId());
            product.setItemId(p.getItemId());
            product.setUnitId(p.getUnitId());
            product.setBatchNo(p.getBatchNo());
            product.setSupplierBatchBarcode(p.getSupplierBatchBarcode());
            product.setProductBarcode(resolveProductBarcode(dto.getBranchId(), p.getItemId(), p.getUnitId(), p.getProductBarcode()));
            product.setInternalBatchBarcode(generateUniqueInternalBatchBarcode(dto.getBranchId(), p.getItemId()));
            product.setCostPrice(costPrice);
            product.setSellingPrice(nvlMoney(p.getSellingPrice()));
            product.setQuantityReceived(qtyReceived);
            product.setQuantityReceivedBase(qtyBase);
            product.setQtyRemaining(qtyBase);
            product.setQtyDamaged(nvlQty(p.getQtyDamaged()));
            product.setQtyExpired(nvlQty(p.getQtyExpired()));
            product.setExpiryDate(p.getExpiryDate());
            product.setLineTotal(lineTotal);
            product.setCreatedAt(LocalDateTime.now());

            SupplyProduct savedProduct = supplyProductRepository.save(product);
            calculatedSubtotal = calculatedSubtotal.add(lineTotal);

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

        BigDecimal discount = nvlMoney(dto.getDiscount());
        BigDecimal taxAmount = nvlMoney(dto.getTaxAmount());
        BigDecimal rounding = nvlMoney(dto.getRounding());

        BigDecimal finalTotal = dto.getTotal() != null
                ? dto.getTotal()
                : calculatedSubtotal.subtract(discount).add(taxAmount).add(rounding);

        BigDecimal paidAmount = nvlMoney(dto.getPaidAmount());
        BigDecimal payableAmount = finalTotal;
        BigDecimal balanceAmount = payableAmount.subtract(paidAmount);

        if (balanceAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Paid amount cannot exceed total");
        }

        savedSupply.setSubtotal(calculatedSubtotal);
        savedSupply.setTotal(finalTotal);
        savedSupply.setPayableAmount(payableAmount);
        savedSupply.setPaidAmount(paidAmount);
        savedSupply.setBalanceAmount(balanceAmount);
        savedSupply.setPaymentStatus(resolvePaymentStatus(payableAmount, paidAmount));

        supplyRepository.save(savedSupply);

        if ("COMPLETED".equalsIgnoreCase(status) && balanceAmount.compareTo(BigDecimal.ZERO) > 0) {
            supplier.setBalance(nvlMoney(supplier.getBalance()).add(balanceAmount));
            supplier.setUpdatedAt(LocalDateTime.now());
            supplierRepository.save(supplier);

            SupplierBalanceTransaction txn = new SupplierBalanceTransaction();
            txn.setBranchId(dto.getBranchId());
            txn.setSupplierId(dto.getSupplierId());
            txn.setType("SUPPLY_CREDIT");
            txn.setAmount(balanceAmount);
            txn.setRefTable("supplies");
            txn.setRefId(savedSupply.getSupplyId());
            txn.setNote("Balance added from completed supply");
            txn.setCreatedBy(receivedBy);
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
                .payableAmount(supply.getPayableAmount())
                .balanceAmount(supply.getBalanceAmount())
                .paymentStatus(supply.getPaymentStatus())
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

    @Override
    public List<SupplyResponseDto> getUnpaidSuppliesByBranch(Long branchId) {
        return supplyRepository
                .findByBranchIdAndPaymentStatusNotOrderBySupplyDateDesc(branchId, "PAID")
                .stream()
                .map(s -> getSupplyById(s.getSupplyId()))
                .toList();
    }

    @Override
    public List<SupplyResponseDto> getUnpaidSuppliesBySupplier(Long branchId, Long supplierId) {
        return supplyRepository
                .findByBranchIdAndSupplierIdAndPaymentStatusNotOrderBySupplyDateDesc(
                        branchId,
                        supplierId,
                        "PAID"
                )
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

        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero");
        }

        String paymentMethod = dto.getPaymentMethod() != null
                ? dto.getPaymentMethod().trim().toUpperCase()
                : "CASH";

        Long paidBy = resolveUserId(dto.getPaidBy());
        Long cashSessionId = null;

        if (requiresCashSession(paymentMethod)) {
            if (dto.getCashSessionId() == null) {
                throw new RuntimeException("Cash session is required for " + paymentMethod + " payment");
            }

            CashSession cashSession = cashSessionRepository.findById(dto.getCashSessionId())
                    .orElseThrow(() -> new RuntimeException("Cash session not found"));

            if (!"OPEN".equalsIgnoreCase(cashSession.getStatus())) {
                throw new RuntimeException("Cash session is not OPEN");
            }

            cashSessionId = cashSession.getSessionId();
        }

        if (dto.getSupplyId() != null) {
            Supply supply = supplyRepository.findById(dto.getSupplyId())
                    .orElseThrow(() -> new RuntimeException("Supply not found"));

            if (!supply.getBranchId().equals(dto.getBranchId())) {
                throw new RuntimeException("Supply does not belong to this branch");
            }

            if (!supply.getSupplierId().equals(dto.getSupplierId())) {
                throw new RuntimeException("Supply does not belong to this supplier");
            }

            BigDecimal newPaid = nvlMoney(supply.getPaidAmount()).add(dto.getAmount());
            BigDecimal total = nvlMoney(supply.getTotal());

            if (newPaid.compareTo(total) > 0) {
                throw new RuntimeException("Payment amount exceeds supply balance");
            }
        }

        SupplierPayment payment = new SupplierPayment();
        payment.setBranchId(dto.getBranchId());
        payment.setSupplierId(dto.getSupplierId());
        payment.setSupplyId(dto.getSupplyId());
        payment.setAmount(nvlMoney(dto.getAmount()));
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentDate(dto.getPaymentDate() != null ? dto.getPaymentDate() : LocalDateTime.now());
        payment.setPaidBy(paidBy);
        payment.setCashSessionId(cashSessionId);
        payment.setReferenceNo(dto.getReferenceNo());
        payment.setNote(dto.getNote());

        SupplierPayment savedPayment = supplierPaymentRepository.save(payment);

        if (dto.getSupplyId() != null) {
            Supply supply = supplyRepository.findById(dto.getSupplyId())
                    .orElseThrow(() -> new RuntimeException("Supply not found"));

            BigDecimal newPaid = nvlMoney(supply.getPaidAmount()).add(payment.getAmount());
            BigDecimal total = nvlMoney(supply.getTotal());
            BigDecimal balance = total.subtract(newPaid);

            supply.setPaidAmount(newPaid);
            supply.setBalanceAmount(balance);
            supply.setPaymentStatus(resolvePaymentStatus(total, newPaid));

            supplyRepository.save(supply);
        }

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
        txn.setNote("Supplier payment recorded by " + paymentMethod);
        txn.setCreatedBy(paidBy);
        txn.setCreatedAt(LocalDateTime.now());
        supplierBalanceTransactionRepository.save(txn);

        if (requiresCashSession(paymentMethod)) {
            CashSessionTransaction cashTxn = new CashSessionTransaction();
            cashTxn.setSessionId(cashSessionId);
            cashTxn.setType("SUPPLIER_PAYMENT_OUT");
            cashTxn.setAmount(payment.getAmount());
            cashTxn.setPaymentMethod(paymentMethod);
            cashTxn.setSupplierPaymentId(savedPayment.getSupplierPaymentId());
            cashTxn.setNote("Supplier payment: " + supplier.getName());
            cashTxn.setCreatedBy(paidBy);
            cashTxn.setCreatedAt(LocalDateTime.now());

            cashSessionTransactionRepository.save(cashTxn);
        }

        return mapSupplierPayment(savedPayment);
    }

    @Override
    public SupplierPaymentResponseDto getSupplierPaymentById(Long paymentId) {
        SupplierPayment payment = supplierPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        return mapSupplierPayment(payment);
    }

    @Override
    public List<SupplierPaymentResponseDto> getPaymentsByBranch(Long branchId) {
        return supplierPaymentRepository.findByBranchIdOrderByPaymentDateDesc(branchId)
                .stream()
                .map(this::mapSupplierPayment)
                .toList();
    }

    @Override
    public List<SupplierPaymentResponseDto> getPaymentsBySupplier(Long branchId, Long supplierId) {
        return supplierPaymentRepository
                .findByBranchIdAndSupplierIdOrderByPaymentDateDesc(branchId, supplierId)
                .stream()
                .map(this::mapSupplierPayment)
                .toList();
    }

    @Override
    public List<SupplierPaymentResponseDto> getPaymentsBySupply(Long supplyId) {
        return supplierPaymentRepository.findBySupplyIdOrderByPaymentDateDesc(supplyId)
                .stream()
                .map(this::mapSupplierPayment)
                .toList();
    }

    // =========================
    // SUPPLIER LEDGER
    // =========================

    @Override
    public List<SupplierBalanceTransactionResponseDto> getSupplierLedger(Long branchId, Long supplierId) {
        return supplierBalanceTransactionRepository
                .findByBranchIdAndSupplierIdOrderByCreatedAtDesc(branchId, supplierId)
                .stream()
                .map(txn -> SupplierBalanceTransactionResponseDto.builder()
                        .txnId(txn.getTxnId())
                        .branchId(txn.getBranchId())
                        .supplierId(txn.getSupplierId())
                        .type(txn.getType())
                        .amount(txn.getAmount())
                        .refTable(txn.getRefTable())
                        .refId(txn.getRefId())
                        .note(txn.getNote())
                        .createdBy(txn.getCreatedBy())
                        .createdAt(txn.getCreatedAt())
                        .build())
                .toList();
    }

    // =========================
    // STOCK HELPERS
    // =========================

    private void addPurchasedStock(Supply supply, SupplyProduct product) {
        ItemUnit baseUnit = itemUnitRepository.findByItemIdAndIsBaseUnitTrue(product.getItemId())
                .orElseThrow(() -> new RuntimeException("Base unit not found for item: " + product.getItemId()));

        Stock stock = stockRepository.findByBranchIdAndItemId(supply.getBranchId(), product.getItemId())
                .orElseGet(() -> {
                    Stock s = new Stock();
                    s.setBranchId(supply.getBranchId());
                    s.setItemId(product.getItemId());
                    s.setUnitId(baseUnit.getUnitId());
                    return s;
                });

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
        batch.setAvailableQty(product.getQtyRemaining());
        batch.setDamagedQty(nvlQty(product.getQtyDamaged()));
        batch.setExpiredQty(nvlQty(product.getQtyExpired()));
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
        movement.setCreatedBy(resolveUserId(supply.getReceivedBy()));
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
    // PAYMENT HELPERS
    // =========================

    private boolean requiresCashSession(String paymentMethod) {
        if (paymentMethod == null) return false;

        return paymentMethod.equalsIgnoreCase("CASH")
                || paymentMethod.equalsIgnoreCase("COUNTER_CASH")
                || paymentMethod.equalsIgnoreCase("COUNTER_PAYMENT");
    }

    private String resolvePaymentStatus(BigDecimal total, BigDecimal paid) {
        total = nvlMoney(total);
        paid = nvlMoney(paid);

        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return "UNPAID";
        }

        if (paid.compareTo(total) >= 0) {
            return "PAID";
        }

        return "PARTIAL";
    }

    // =========================
// PURCHASE RETURNS
// =========================

@Override
public PurchaseReturnResponseDto createPurchaseReturn(PurchaseReturnRequestDto dto) {

    Supplier supplier = supplierRepository.findById(dto.getSupplierId())
            .orElseThrow(() -> new RuntimeException("Supplier not found"));

    if (!supplier.getBranchId().equals(dto.getBranchId())) {
        throw new RuntimeException("Supplier does not belong to this branch");
    }

    if (dto.getItems() == null || dto.getItems().isEmpty()) {
        throw new RuntimeException("Return items cannot be empty");
    }

    Long processedBy = resolveUserId(dto.getProcessedBy());

    BigDecimal calculatedRefundAmount = BigDecimal.ZERO;

    PurchaseReturn purchaseReturn = new PurchaseReturn();
    String refundMethod = dto.getRefundMethod() != null ? dto.getRefundMethod() : "BALANCE_ADJUSTMENT";
    Long cashSessionId = null;

    if (isBankRefundMethod(refundMethod) && isBlank(dto.getBankReference())) {
        throw new RuntimeException("Bank reference is required for bank refund");
    }

    if (isCashRefundMethod(refundMethod)) {
        if (dto.getCashSessionId() == null) {
            throw new RuntimeException("Cash session is required for cash refund");
        }

        CashSession cashSession = cashSessionRepository.findById(dto.getCashSessionId())
                .orElseThrow(() -> new RuntimeException("Cash session not found"));

        if (!"OPEN".equalsIgnoreCase(cashSession.getStatus())) {
            throw new RuntimeException("Cash session is not OPEN");
        }

        cashSessionId = cashSession.getSessionId();
    }

    purchaseReturn.setBranchId(dto.getBranchId());
    purchaseReturn.setSupplierId(dto.getSupplierId());
    purchaseReturn.setSupplyId(dto.getSupplyId());
    purchaseReturn.setReturnDate(dto.getReturnDate() != null ? dto.getReturnDate() : LocalDateTime.now());
    purchaseReturn.setRefundMethod(refundMethod);
    purchaseReturn.setCashSessionId(cashSessionId);
    purchaseReturn.setBankReference(clean(dto.getBankReference()));
    purchaseReturn.setRefundAmount(BigDecimal.ZERO);
    purchaseReturn.setReason(dto.getReason());
    purchaseReturn.setProcessedBy(processedBy);
    purchaseReturn.setStatus(dto.getStatus() != null ? dto.getStatus() : "COMPLETED");

    PurchaseReturn savedReturn = purchaseReturnRepository.save(purchaseReturn);

    for (PurchaseReturnItemRequestDto itemDto : dto.getItems()) {

        validateItemAndUnit(dto.getBranchId(), itemDto.getItemId(), itemDto.getUnitId());

        BigDecimal qty = nvlQty(itemDto.getQuantity());
        BigDecimal unitCost = nvlMoney(itemDto.getUnitCost());
        BigDecimal lineTotal = itemDto.getLineTotal() != null
                ? itemDto.getLineTotal()
                : unitCost.multiply(qty);

        String returnStockType = itemDto.getReturnStockType() != null
                ? itemDto.getReturnStockType().trim().toUpperCase()
                : "AVAILABLE";

        PurchaseReturnItem returnItem = new PurchaseReturnItem();
        returnItem.setPurchaseReturnId(savedReturn.getPurchaseReturnId());
        returnItem.setItemId(itemDto.getItemId());
        returnItem.setUnitId(itemDto.getUnitId());
        returnItem.setInternalBatchBarcode(itemDto.getInternalBatchBarcode());
        returnItem.setReturnStockType(returnStockType);
        returnItem.setQuantity(qty);
        returnItem.setUnitCost(unitCost);
        returnItem.setLineTotal(lineTotal);

        purchaseReturnItemRepository.save(returnItem);

        reduceStockForPurchaseReturn(dto, itemDto, savedReturn.getPurchaseReturnId(), processedBy);

        calculatedRefundAmount = calculatedRefundAmount.add(lineTotal);
    }

    BigDecimal refundAmount = dto.getRefundAmount() != null
            ? dto.getRefundAmount()
            : calculatedRefundAmount;

    savedReturn.setRefundAmount(refundAmount);
    purchaseReturnRepository.save(savedReturn);

    if ("COMPLETED".equalsIgnoreCase(savedReturn.getStatus())) {

        supplier.setBalance(nvlMoney(supplier.getBalance()).subtract(refundAmount));
        supplier.setUpdatedAt(LocalDateTime.now());
        supplierRepository.save(supplier);

        SupplierBalanceTransaction txn = new SupplierBalanceTransaction();
        txn.setBranchId(dto.getBranchId());
        txn.setSupplierId(dto.getSupplierId());
        txn.setType("PURCHASE_RETURN");
        txn.setAmount(refundAmount);
        txn.setRefTable("purchase_returns");
        txn.setRefId(savedReturn.getPurchaseReturnId());
        txn.setNote(buildPurchaseReturnTxnNote(savedReturn));
        txn.setCreatedBy(processedBy);
        txn.setCreatedAt(LocalDateTime.now());
        supplierBalanceTransactionRepository.save(txn);

        if (isCashRefundMethod(savedReturn.getRefundMethod())) {
            CashSessionTransaction cashTxn = new CashSessionTransaction();
            cashTxn.setSessionId(savedReturn.getCashSessionId());
            cashTxn.setType("SUPPLIER_REFUND_IN");
            cashTxn.setAmount(refundAmount);
            cashTxn.setPaymentMethod("CASH");
            cashTxn.setPurchaseReturnId(savedReturn.getPurchaseReturnId());
            cashTxn.setNote("Supplier cash refund: " + supplier.getName());
            cashTxn.setCreatedBy(processedBy);
            cashTxn.setCreatedAt(LocalDateTime.now());
            cashSessionTransactionRepository.save(cashTxn);
        }

        if (dto.getSupplyId() != null) {
            Supply supply = supplyRepository.findById(dto.getSupplyId())
                    .orElseThrow(() -> new RuntimeException("Supply not found"));

            BigDecimal newBalance = nvlMoney(supply.getBalanceAmount()).subtract(refundAmount);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                newBalance = BigDecimal.ZERO;
            }

            BigDecimal newPaidLikeValue = nvlMoney(supply.getTotal()).subtract(newBalance);

            supply.setBalanceAmount(newBalance);
            supply.setPaidAmount(newPaidLikeValue);
            supply.setPaymentStatus(resolvePaymentStatus(supply.getTotal(), newPaidLikeValue));

            supplyRepository.save(supply);
        }
    }

    return getPurchaseReturnById(savedReturn.getPurchaseReturnId());
}

@Override
public PurchaseReturnResponseDto getPurchaseReturnById(Long purchaseReturnId) {
    PurchaseReturn purchaseReturn = purchaseReturnRepository.findById(purchaseReturnId)
            .orElseThrow(() -> new RuntimeException("Purchase return not found"));

    List<PurchaseReturnItemResponseDto> items = purchaseReturnItemRepository
            .findByPurchaseReturnId(purchaseReturnId)
            .stream()
            .map(this::mapPurchaseReturnItem)
            .toList();

    return mapPurchaseReturn(purchaseReturn, items);
}

@Override
public List<PurchaseReturnResponseDto> getPurchaseReturnsByBranch(Long branchId) {
    return purchaseReturnRepository.findByBranchIdOrderByReturnDateDesc(branchId)
            .stream()
            .map(pr -> getPurchaseReturnById(pr.getPurchaseReturnId()))
            .toList();
}

@Override
public List<PurchaseReturnResponseDto> getPurchaseReturnsBySupplier(Long branchId, Long supplierId) {
    return purchaseReturnRepository.findByBranchIdAndSupplierIdOrderByReturnDateDesc(branchId, supplierId)
            .stream()
            .map(pr -> getPurchaseReturnById(pr.getPurchaseReturnId()))
            .toList();
}

@Override
public List<PurchaseReturnResponseDto> getPurchaseReturnsBySupply(Long supplyId) {
    return purchaseReturnRepository.findBySupplyIdOrderByReturnDateDesc(supplyId)
            .stream()
            .map(pr -> getPurchaseReturnById(pr.getPurchaseReturnId()))
            .toList();
}
    // =========================
    // RESOLVE HELPERS
    // =========================

    private Long resolveUserId(Long userId) {
        return userId != null ? userId : SYSTEM_USER_ID;
    }

    private String resolvePoNo(String poNo) {
        if (poNo != null && !poNo.isBlank()) {
            return poNo.trim();
        }

        return "PO-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String resolveGrnNo(String grnNo) {
        if (grnNo != null && !grnNo.isBlank()) {
            return grnNo.trim();
        }

        return "GRN-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private void reduceStockForPurchaseReturn(
        PurchaseReturnRequestDto dto,
        PurchaseReturnItemRequestDto itemDto,
        Long purchaseReturnId,
        Long processedBy
) {
    String type = itemDto.getReturnStockType() != null
            ? itemDto.getReturnStockType().trim().toUpperCase()
            : "AVAILABLE";

    BigDecimal qtyBase = unitConversionService.toBaseQty(
            itemDto.getItemId(),
            itemDto.getUnitId(),
            nvlQty(itemDto.getQuantity())
    );

    Stock stock = stockRepository.findByBranchIdAndItemId(dto.getBranchId(), itemDto.getItemId())
            .orElseThrow(() -> new RuntimeException("Stock not found"));

    switch (type) {
        case "DAMAGED" -> {
            if (getBatchQtyTotal(dto.getBranchId(), itemDto.getItemId(), StockQtyType.DAMAGED).compareTo(qtyBase) < 0) {
                throw new RuntimeException("Not enough damaged stock to return");
            }
        }

        case "EXPIRED" -> {
            if (getBatchQtyTotal(dto.getBranchId(), itemDto.getItemId(), StockQtyType.EXPIRED).compareTo(qtyBase) < 0) {
                throw new RuntimeException("Not enough expired stock to return");
            }
        }

        default -> {
            if (getBatchQtyTotal(dto.getBranchId(), itemDto.getItemId(), StockQtyType.AVAILABLE).compareTo(qtyBase) < 0) {
                throw new RuntimeException("Not enough available stock to return");
            }
        }
    }

    stock.setLastUpdated(LocalDateTime.now());
    stockRepository.save(stock);

    if (itemDto.getInternalBatchBarcode() != null && !itemDto.getInternalBatchBarcode().isBlank()) {
        StockBatch batch = stockBatchRepository
                .findByBranchIdAndInternalBatchBarcode(dto.getBranchId(), itemDto.getInternalBatchBarcode())
                .orElseThrow(() -> new RuntimeException("Stock batch not found"));

        BigDecimal batchQty = switch (type) {
            case "DAMAGED" -> nvlQty(batch.getDamagedQty());
            case "EXPIRED" -> nvlQty(batch.getExpiredQty());
            default -> nvlQty(batch.getAvailableQty());
        };

        if (batchQty.compareTo(qtyBase) < 0) {
            throw new RuntimeException("Not enough batch " + type.toLowerCase() + " quantity to return");
        }

        switch (type) {
            case "DAMAGED" -> batch.setDamagedQty(nvlQty(batch.getDamagedQty()).subtract(qtyBase));
            case "EXPIRED" -> batch.setExpiredQty(nvlQty(batch.getExpiredQty()).subtract(qtyBase));
            default -> {
                batch.setAvailableQty(nvlQty(batch.getAvailableQty()).subtract(qtyBase));
                batch.setQtyRemaining(nvlQty(batch.getQtyRemaining()).subtract(qtyBase));
            }
        }
        stockBatchRepository.save(batch);
    } else {
        deductBatchQtyFifo(dto.getBranchId(), itemDto.getItemId(), qtyBase, type);
    }

    StockMovement movement = new StockMovement();
    movement.setBranchId(dto.getBranchId());
    movement.setMovementType("PURCHASE_RETURN_OUT");
    movement.setItemId(itemDto.getItemId());
    movement.setUnitId(itemDto.getUnitId());
    movement.setInternalBatchBarcode(itemDto.getInternalBatchBarcode());
    movement.setQuantity(qtyBase);
    movement.setUnitCost(nvlMoney(itemDto.getUnitCost()));
    movement.setUnitPrice(null);
    movement.setRefTable("purchase_returns");
    movement.setRefId(purchaseReturnId);
    movement.setNote("Purchase return from " + type + " stock");
    movement.setCreatedBy(processedBy);
    movement.setCreatedAt(LocalDateTime.now());

    stockMovementRepository.save(movement);
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

    private SupplierPaymentResponseDto mapSupplierPayment(SupplierPayment payment) {
        return SupplierPaymentResponseDto.builder()
                .supplierPaymentId(payment.getSupplierPaymentId())
                .branchId(payment.getBranchId())
                .supplierId(payment.getSupplierId())
                .supplyId(payment.getSupplyId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .paymentDate(payment.getPaymentDate())
                .paidBy(payment.getPaidBy())
                .cashSessionId(payment.getCashSessionId())
                .referenceNo(payment.getReferenceNo())
                .note(payment.getNote())
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

    private void deductBatchQtyFifo(Long branchId, Long itemId, BigDecimal qtyBase, String type) {
        List<StockBatch> batches = stockBatchRepository.findByBranchIdAndItemIdOrderByCreatedAtDesc(branchId, itemId);
        java.util.Collections.reverse(batches);

        BigDecimal remaining = qtyBase;
        for (StockBatch batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal batchQty = switch (type) {
                case "DAMAGED" -> nvlQty(batch.getDamagedQty());
                case "EXPIRED" -> nvlQty(batch.getExpiredQty());
                default -> nvlQty(batch.getAvailableQty() != null ? batch.getAvailableQty() : batch.getQtyRemaining());
            };
            if (batchQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal deduct = remaining.min(batchQty);
            switch (type) {
                case "DAMAGED" -> batch.setDamagedQty(nvlQty(batch.getDamagedQty()).subtract(deduct));
                case "EXPIRED" -> batch.setExpiredQty(nvlQty(batch.getExpiredQty()).subtract(deduct));
                default -> {
                    batch.setAvailableQty(nvlQty(batch.getAvailableQty()).subtract(deduct));
                    batch.setQtyRemaining(nvlQty(batch.getQtyRemaining()).subtract(deduct));
                }
            }
            stockBatchRepository.save(batch);
            remaining = remaining.subtract(deduct);
        }
    }

    private enum StockQtyType {
        AVAILABLE,
        DAMAGED,
        EXPIRED
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String clean(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isBankRefundMethod(String refundMethod) {
        if (refundMethod == null) {
            return false;
        }
        String normalized = refundMethod.trim().toUpperCase();
        return "BANK_TRANSFER".equals(normalized) || "BANK_REFUND".equals(normalized);
    }

    private boolean isCashRefundMethod(String refundMethod) {
        if (refundMethod == null) {
            return false;
        }
        String normalized = refundMethod.trim().toUpperCase();
        return "CASH".equals(normalized) || "CASH_REFUND".equals(normalized) || "COUNTER_CASH".equals(normalized);
    }

    private String buildPurchaseReturnTxnNote(PurchaseReturn purchaseReturn) {
        String note = "Purchase return processed";
        if (isBankRefundMethod(purchaseReturn.getRefundMethod()) && !isBlank(purchaseReturn.getBankReference())) {
            note += " - Bank reference: " + purchaseReturn.getBankReference().trim();
        }
        return note;
    }

    private PurchaseReturnResponseDto mapPurchaseReturn(
        PurchaseReturn purchaseReturn,
        List<PurchaseReturnItemResponseDto> items
) {
    return PurchaseReturnResponseDto.builder()
            .purchaseReturnId(purchaseReturn.getPurchaseReturnId())
            .branchId(purchaseReturn.getBranchId())
            .supplierId(purchaseReturn.getSupplierId())
            .supplyId(purchaseReturn.getSupplyId())
            .returnDate(purchaseReturn.getReturnDate())
            .refundMethod(purchaseReturn.getRefundMethod())
            .cashSessionId(purchaseReturn.getCashSessionId())
            .bankReference(purchaseReturn.getBankReference())
            .refundAmount(purchaseReturn.getRefundAmount())
            .reason(purchaseReturn.getReason())
            .processedBy(purchaseReturn.getProcessedBy())
            .status(purchaseReturn.getStatus())
            .items(items)
            .build();
}

private PurchaseReturnItemResponseDto mapPurchaseReturnItem(PurchaseReturnItem item) {
    return PurchaseReturnItemResponseDto.builder()
            .purchaseReturnItemId(item.getPurchaseReturnItemId())
            .purchaseReturnId(item.getPurchaseReturnId())
            .itemId(item.getItemId())
            .unitId(item.getUnitId())
            .internalBatchBarcode(item.getInternalBatchBarcode())
            .returnStockType(item.getReturnStockType())
            .quantity(item.getQuantity())
            .unitCost(item.getUnitCost())
            .lineTotal(item.getLineTotal())
            .build();
}
}
