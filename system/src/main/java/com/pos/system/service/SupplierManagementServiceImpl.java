package com.pos.system.service;

import com.pos.system.dto.supplier.*;
import com.pos.system.model.cash.CashSession;
import com.pos.system.model.cash.CashSessionTransaction;
import com.pos.system.model.cash.Counter;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.ItemVariant;
import com.pos.system.model.catalog.UnitMaster;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.model.sale.Payment;
import com.pos.system.model.stock.Stock;
import com.pos.system.model.stock.StockBatch;
import com.pos.system.model.stock.StockMovement;
import com.pos.system.model.supplier.*;
import com.pos.system.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.RoundingMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final SupplierPaymentAllocationRepository supplierPaymentAllocationRepository;
    private final SupplierBalanceTransactionRepository supplierBalanceTransactionRepository;

    private final StockRepository stockRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockMovementRepository stockMovementRepository;

    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final ItemVariantAttributeRepository itemVariantAttributeRepository;
    private final UnitMasterRepository unitMasterRepository;
    private final UserRepository userRepository;
    private final UnitConversionService unitConversionService;

    private final CashSessionRepository cashSessionRepository;
    private final CashSessionTransactionRepository cashSessionTransactionRepository;
    private final CounterRepository counterRepository;
    private final CustomerOrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    private final PurchaseReturnRepository purchaseReturnRepository;
    private final PurchaseReturnItemRepository purchaseReturnItemRepository;
    private final PurchaseReturnRefundRepository purchaseReturnRefundRepository;

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
        supplier.setAdvanceCredit(BigDecimal.ZERO);
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

        validateItemUnitAndVariant(dto.getBranchId(), dto.getItemId(), dto.getUnitId(), dto.getVariantId());

        supplierItemRepository
                .findByBranchIdAndSupplierIdAndItemIdAndVariantIdAndUnitId(
                        dto.getBranchId(),
                        dto.getSupplierId(),
                        dto.getItemId(),
                        dto.getVariantId(),
                        dto.getUnitId()
                )
                .ifPresent(x -> {
                    throw new RuntimeException("Supplier already supplies this item/variant with this unit");
                });

        SupplierItem supplierItem = new SupplierItem();
        supplierItem.setBranchId(dto.getBranchId());
        supplierItem.setSupplierId(dto.getSupplierId());
        supplierItem.setItemId(dto.getItemId());
        supplierItem.setVariantId(dto.getVariantId());
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
    public List<SupplierItemResponseDto> addSupplierItemsBulk(BulkSupplierItemRequestDto dto) {
        if (dto == null || dto.getBranchId() == null || dto.getSupplierId() == null) {
            throw new RuntimeException("Branch and supplier are required");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new RuntimeException("At least one supplier item is required");
        }

        return dto.getItems().stream()
                .map(item -> {
                    if (item == null) {
                        throw new RuntimeException("Supplier item cannot be null");
                    }

                    SupplierItemRequestDto request = new SupplierItemRequestDto();
                    request.setBranchId(dto.getBranchId());
                    request.setSupplierId(dto.getSupplierId());
                    request.setItemId(item.getItemId());
                    request.setVariantId(item.getVariantId());
                    request.setUnitId(item.getUnitId());
                    request.setLastPurchaseCost(item.getLastPurchaseCost());
                    request.setDefaultCostPrice(item.getDefaultCostPrice());
                    request.setIsPreferred(item.getIsPreferred());
                    request.setIsActive(item.getIsActive());
                    return addSupplierItem(request);
                })
                .toList();
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

        validateItemUnitAndVariant(dto.getBranchId(), dto.getItemId(), dto.getUnitId(), dto.getVariantId());

        supplierItem.setBranchId(dto.getBranchId());
        supplierItem.setSupplierId(dto.getSupplierId());
        supplierItem.setItemId(dto.getItemId());
        supplierItem.setVariantId(dto.getVariantId());
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

        List<PurchaseOrderItemRequestDto> normalizedItems = normalizePurchaseOrderItems(dto.getItems());

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

        for (PurchaseOrderItemRequestDto itemDto : normalizedItems) {
            validateItemUnitAndVariant(dto.getBranchId(), itemDto.getItemId(), itemDto.getUnitId(), itemDto.getVariantId());

            validateSupplierProvidesItem(
                    dto.getBranchId(),
                    dto.getSupplierId(),
                    itemDto.getItemId(),
                    itemDto.getVariantId(),
                    itemDto.getUnitId()
            );

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPoId(savedPo.getPoId());
            item.setItemId(itemDto.getItemId());
            item.setVariantId(itemDto.getVariantId());
            item.setUnitId(itemDto.getUnitId());
            item.setOrderedQty(nvlQty(itemDto.getOrderedQty()));
            item.setReceivedQty(BigDecimal.ZERO);
            item.setUnitCostEst(nvlMoney(itemDto.getUnitCostEst()));

            try {
                purchaseOrderItemRepository.save(item);
            } catch (DataIntegrityViolationException ex) {
                throw new RuntimeException("Duplicate purchase order item/unit combination detected. If this uses an existing database, restart once so the PO item unique index can be repaired.", ex);
            }
        }

        return getPurchaseOrderById(savedPo.getPoId());
    }

    @Override
    public PurchaseOrderResponseDto getPurchaseOrderById(Long poId) {
        purchaseOrderItemRepository.flush();
        supplierPaymentRepository.flush();
        supplyRepository.flush();

        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));
        PurchaseOrderResponseDto response = mapPurchaseOrderListRows(List.of(po)).stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));
        List<SupplyResponseDto> receipts = supplyRepository.findByPoId(poId)
                .stream()
                .map(supply -> getSupplyById(supply.getSupplyId()))
                .toList();
        List<SupplierPaymentResponseDto> payments = supplierPaymentRepository.findByPoId(poId)
                .stream()
                .map(this::mapSupplierPayment)
                .toList();
        List<Long> receiptIds = receipts.stream()
                .map(SupplyResponseDto::getSupplyId)
                .toList();
        List<PurchaseReturnResponseDto> purchaseReturns = receiptIds.isEmpty()
                ? List.of()
                : purchaseReturnRepository.findBySupplyIdInOrderByReturnDateDesc(receiptIds)
                .stream()
                .map(purchaseReturn -> getPurchaseReturnById(purchaseReturn.getPurchaseReturnId()))
                .toList();

        response.setReceipts(receipts);
        response.setPayments(payments);
        response.setPurchaseReturns(purchaseReturns);
        return response;
    }

    @Override
    public List<PurchaseOrderResponseDto> getPurchaseOrdersByBranch(Long branchId) {
        return purchaseOrderRepository.findByBranchId(branchId)
                .stream()
                .map(po -> getPurchaseOrderById(po.getPoId()))
                .toList();
    }

    @Override
    public List<PurchaseOrderResponseDto> getPurchaseOrdersByItem(Long branchId, Long itemId) {
        return purchaseOrderRepository.findByBranchIdAndItemId(branchId, itemId)
                .stream().map(po -> getPurchaseOrderById(po.getPoId())).toList();
    }

    @Override
    public PurchaseOrderPageResponseDto searchPurchaseOrdersByBranch(
            Long branchId,
            PurchaseOrderSearchRequestDto request,
            Pageable pageable
    ) {
        PurchaseOrderSearchRequestDto filters = request != null ? request : new PurchaseOrderSearchRequestDto();
        purchaseOrderItemRepository.flush();
        supplierPaymentRepository.flush();
        supplyRepository.flush();

        List<PurchaseOrder> candidates = purchaseOrderRepository.findAll(buildPurchaseOrderSearchSpecification(branchId, filters));
        List<PurchaseOrderResponseDto> rows = mapPurchaseOrderListRows(candidates);

        rows = rows.stream()
                .filter(row -> matchesStatusFilter(row.getStatus(), filters.getStatus()))
                .filter(row -> matchesExactStatus(row.getReceivingStatus(), filters.getReceivingStatus()))
                .filter(row -> matchesExactStatus(row.getPaymentStatus(), filters.getPaymentStatus()))
                .toList();

        PurchaseOrderSummaryDto summary = buildPurchaseOrderSummary(rows);
        List<PurchaseOrderResponseDto> sortedRows = new ArrayList<>(rows);
        sortedRows.sort(buildPurchaseOrderComparator(pageable));

        int pageSize = pageable.getPageSize() > 0 ? pageable.getPageSize() : 25;
        int pageNumber = Math.max(pageable.getPageNumber(), 0);
        int fromIndex = Math.min(pageNumber * pageSize, sortedRows.size());
        int toIndex = Math.min(fromIndex + pageSize, sortedRows.size());

        return PurchaseOrderPageResponseDto.builder()
                .content(sortedRows.subList(fromIndex, toIndex))
                .page(pageNumber)
                .pageSize(pageSize)
                .totalElements(sortedRows.size())
                .totalPages(pageSize == 0 ? 0 : (int) Math.ceil((double) sortedRows.size() / pageSize))
                .sort(formatPageSort(pageable))
                .summary(summary)
                .build();
    }

    private Specification<PurchaseOrder> buildPurchaseOrderSearchSpecification(
            Long branchId,
            PurchaseOrderSearchRequestDto request
    ) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("branchId"), branchId));

            if (request.getSupplierId() != null) {
                predicates.add(cb.equal(root.get("supplierId"), request.getSupplierId()));
            }

            if (request.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), request.getDateFrom().atStartOfDay()));
            }

            if (request.getDateTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), request.getDateTo().plusDays(1).atStartOfDay()));
            }

            if (StringUtils.hasText(request.getQ())) {
                String pattern = "%" + request.getQ().trim().toLowerCase() + "%";

                var supplierSubquery = query.subquery(Long.class);
                var supplierRoot = supplierSubquery.from(Supplier.class);
                supplierSubquery.select(supplierRoot.get("supplierId"))
                        .where(
                                cb.equal(supplierRoot.get("supplierId"), root.get("supplierId")),
                                cb.or(
                                        cb.like(cb.lower(supplierRoot.get("name")), pattern),
                                        cb.like(cb.lower(supplierRoot.get("contactPerson")), pattern),
                                        cb.like(cb.lower(supplierRoot.get("phone")), pattern),
                                        cb.like(cb.lower(supplierRoot.get("email")), pattern)
                                )
                        );

                var itemSubquery = query.subquery(Long.class);
                var lineRoot = itemSubquery.from(PurchaseOrderItem.class);
                var itemRoot = itemSubquery.from(Item.class);
                itemSubquery.select(lineRoot.get("poItemId"))
                        .where(
                                cb.equal(lineRoot.get("poId"), root.get("poId")),
                                cb.equal(lineRoot.get("itemId"), itemRoot.get("itemId")),
                                cb.or(
                                        cb.like(cb.lower(itemRoot.get("name")), pattern),
                                        cb.like(cb.lower(itemRoot.get("sku")), pattern)
                                )
                        );

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("poNo")), pattern),
                        cb.like(cb.lower(root.get("status")), pattern),
                        cb.like(cb.lower(root.get("note")), pattern),
                        cb.exists(supplierSubquery),
                        cb.exists(itemSubquery)
                ));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private List<PurchaseOrderResponseDto> mapPurchaseOrderListRows(List<PurchaseOrder> purchaseOrders) {
        if (purchaseOrders == null || purchaseOrders.isEmpty()) {
            return List.of();
        }

        List<Long> poIds = purchaseOrders.stream().map(PurchaseOrder::getPoId).toList();
        Map<Long, List<PurchaseOrderItem>> itemsByPoId = groupByPoId(purchaseOrderItemRepository.findByPoIdIn(poIds));
        Map<Long, List<Supply>> suppliesByPoId = groupSuppliesByPoId(supplyRepository.findByPoIdIn(poIds));
        Map<Long, List<SupplierPayment>> paymentsByPoId = groupPaymentsByPoId(supplierPaymentRepository.findByPoIdIn(poIds));
        if (purchaseOrders.size() == 1) {
            Long poId = purchaseOrders.get(0).getPoId();
            itemsByPoId.computeIfAbsent(poId, id -> purchaseOrderItemRepository.findByPoId(id));
            suppliesByPoId.computeIfAbsent(poId, id -> supplyRepository.findByPoId(id));
            paymentsByPoId.computeIfAbsent(poId, id -> supplierPaymentRepository.findByPoId(id));
        }

        Map<Long, Supplier> suppliersById = new LinkedHashMap<>();
        supplierRepository.findAllById(
                purchaseOrders.stream().map(PurchaseOrder::getSupplierId).filter(id -> id != null).distinct().toList()
        ).forEach(supplier -> suppliersById.put(supplier.getSupplierId(), supplier));

        Set<Long> itemIds = new HashSet<>();
        Set<Long> unitIds = new HashSet<>();
        Set<Long> variantIds = new HashSet<>();
        itemsByPoId.values().forEach(items -> items.forEach(item -> {
            if (item.getItemId() != null) itemIds.add(item.getItemId());
            if (item.getUnitId() != null) unitIds.add(item.getUnitId());
            if (item.getVariantId() != null) variantIds.add(item.getVariantId());
        }));

        Map<Long, Item> catalogItemsById = new LinkedHashMap<>();
        itemRepository.findAllById(itemIds).forEach(item -> catalogItemsById.put(item.getItemId(), item));

        Map<Long, ItemUnit> unitsById = new LinkedHashMap<>();
        itemUnitRepository.findAllById(unitIds).forEach(unit -> unitsById.put(unit.getUnitId(), unit));

        Map<Long, UnitMaster> masterUnitsById = new LinkedHashMap<>();
        unitMasterRepository.findAllById(
                unitsById.values().stream().map(ItemUnit::getMasterUnitId).filter(id -> id != null).distinct().toList()
        ).forEach(unit -> masterUnitsById.put(unit.getUnitId(), unit));

        Map<Long, ItemVariant> variantsById = new LinkedHashMap<>();
        itemVariantRepository.findAllById(variantIds).forEach(variant -> variantsById.put(variant.getVariantId(), variant));

        Map<Long, String> variantLabelsById = buildVariantLabelMap(variantIds, variantsById);

        List<PurchaseOrderResponseDto> rows = new ArrayList<>();
        for (PurchaseOrder po : purchaseOrders) {
            List<PurchaseOrderItemResponseDto> itemDtos = (itemsByPoId.getOrDefault(po.getPoId(), List.of()))
                    .stream()
                    .map(item -> mapPurchaseOrderItemForList(item, catalogItemsById, unitsById, masterUnitsById, variantsById, variantLabelsById))
                    .toList();
            BigDecimal totalAmount = itemDtos.stream()
                    .map(item -> nvlQty(item.getOrderedQty()).multiply(nvlMoney(item.getUnitCostEst())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal orderedQuantity = itemDtos.stream()
                    .map(item -> nvlQty(item.getOrderedQty()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal receivedQuantity = itemDtos.stream()
                    .map(item -> nvlQty(item.getReceivedQty()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remainingQuantity = itemDtos.stream()
                    .map(item -> nvlQty(item.getRemainingQty()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paidAmount = calculatePurchaseOrderPaidAmount(
                    po.getPoId(),
                    paymentsByPoId.getOrDefault(po.getPoId(), List.of()),
                    suppliesByPoId.getOrDefault(po.getPoId(), List.of())
            );
            BigDecimal balanceAmount = totalAmount.subtract(paidAmount).max(BigDecimal.ZERO);
            String receivingStatus = resolvePurchaseOrderReceivingStatus(itemDtos, po.getStatus());
            Supplier supplier = suppliersById.get(po.getSupplierId());

            rows.add(PurchaseOrderResponseDto.builder()
                    .poId(po.getPoId())
                    .branchId(po.getBranchId())
                    .supplierId(po.getSupplierId())
                    .supplierName(supplier != null ? supplier.getName() : null)
                    .supplierContactPerson(supplier != null ? supplier.getContactPerson() : null)
                    .supplierPhone(supplier != null ? supplier.getPhone() : null)
                    .supplierEmail(supplier != null ? supplier.getEmail() : null)
                    .supplierAddress(supplier != null ? supplier.getAddress() : null)
                    .poNo(po.getPoNo())
                    .status(po.getStatus())
                    .orderDate(po.getCreatedAt() != null ? po.getCreatedAt().toLocalDate() : null)
                    .expectedDate(po.getExpectedDate())
                    .createdBy(po.getCreatedBy())
                    .createdAt(po.getCreatedAt())
                    .note(po.getNote())
                    .totalAmount(totalAmount)
                    .paidAmount(paidAmount)
                    .balanceAmount(balanceAmount)
                    .receivingStatus(receivingStatus)
                    .paymentStatus(resolvePaymentStatus(totalAmount, paidAmount))
                    .itemCount(itemDtos.size())
                    .orderedQuantity(orderedQuantity)
                    .receivedQuantity(receivedQuantity)
                    .remainingQuantity(remainingQuantity)
                    .overdue(isPurchaseOrderOverdue(po.getExpectedDate(), receivingStatus, po.getStatus()))
                    .items(itemDtos)
                    .build());
        }

        return rows;
    }

    private PurchaseOrderItemResponseDto mapPurchaseOrderItemForList(
            PurchaseOrderItem item,
            Map<Long, Item> catalogItemsById,
            Map<Long, ItemUnit> unitsById,
            Map<Long, UnitMaster> masterUnitsById,
            Map<Long, ItemVariant> variantsById,
            Map<Long, String> variantLabelsById
    ) {
        Item catalogItem = catalogItemsById.get(item.getItemId());
        ItemUnit unit = unitsById.get(item.getUnitId());
        ItemVariant variant = variantsById.get(item.getVariantId());
        String unitName = null;
        if (unit != null) {
            UnitMaster masterUnit = masterUnitsById.get(unit.getMasterUnitId());
            unitName = masterUnit != null ? masterUnit.getName() : unit.getUnitName();
        }

        return PurchaseOrderItemResponseDto.builder()
                .poItemId(item.getPoItemId())
                .poId(item.getPoId())
                .itemId(item.getItemId())
                .itemName(catalogItem != null ? catalogItem.getName() : null)
                .itemImage(catalogItem != null ? catalogItem.getImage() : null)
                .sku(catalogItem != null ? catalogItem.getSku() : null)
                .variantId(item.getVariantId())
                .variantSku(variant != null ? variant.getSku() : null)
                .variantLabel(variantLabelsById.get(item.getVariantId()))
                .variantImage(variant != null ? variant.getImage() : null)
                .unitId(item.getUnitId())
                .masterUnitId(unit != null ? unit.getMasterUnitId() : null)
                .unitName(unitName)
                .orderedQty(item.getOrderedQty())
                .receivedQty(item.getReceivedQty())
                .remainingQty(nvlQty(item.getOrderedQty()).subtract(nvlQty(item.getReceivedQty())).max(BigDecimal.ZERO))
                .unitCostEst(item.getUnitCostEst())
                .build();
    }

    private Map<Long, List<PurchaseOrderItem>> groupByPoId(List<PurchaseOrderItem> items) {
        Map<Long, List<PurchaseOrderItem>> grouped = new LinkedHashMap<>();
        for (PurchaseOrderItem item : items) {
            grouped.computeIfAbsent(item.getPoId(), id -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private Map<Long, List<Supply>> groupSuppliesByPoId(List<Supply> supplies) {
        Map<Long, List<Supply>> grouped = new LinkedHashMap<>();
        for (Supply supply : supplies) {
            if (supply.getPoId() == null) continue;
            grouped.computeIfAbsent(supply.getPoId(), id -> new ArrayList<>()).add(supply);
        }
        return grouped;
    }

    private Map<Long, List<SupplierPayment>> groupPaymentsByPoId(List<SupplierPayment> payments) {
        Map<Long, List<SupplierPayment>> grouped = new LinkedHashMap<>();
        for (SupplierPayment payment : payments) {
            if (payment.getPoId() == null) continue;
            grouped.computeIfAbsent(payment.getPoId(), id -> new ArrayList<>()).add(payment);
        }
        return grouped;
    }

    private Map<Long, String> buildVariantLabelMap(Collection<Long> variantIds, Map<Long, ItemVariant> variantsById) {
        Map<Long, List<String>> attributesByVariant = new LinkedHashMap<>();
        if (variantIds != null && !variantIds.isEmpty()) {
            itemVariantAttributeRepository.findByVariantIdIn(variantIds)
                    .stream()
                    .sorted(Comparator.comparing(attribute -> safe(attribute.getAttributeName())))
                    .forEach(attribute -> attributesByVariant
                            .computeIfAbsent(attribute.getVariantId(), id -> new ArrayList<>())
                            .add(attribute.getAttributeName() + ": " + attribute.getAttributeValue()));
        }

        Map<Long, String> labelsByVariant = new LinkedHashMap<>();
        for (Long variantId : variantIds) {
            List<String> attributes = attributesByVariant.getOrDefault(variantId, List.of());
            String label = String.join(" / ", attributes);
            if (label.isBlank()) {
                ItemVariant variant = variantsById.get(variantId);
                label = variant != null ? variant.getSku() : null;
            }
            labelsByVariant.put(variantId, label);
        }
        return labelsByVariant;
    }

    private BigDecimal calculatePurchaseOrderPaidAmount(Long poId, List<SupplierPayment> payments, List<Supply> supplies) {
        BigDecimal directPoPayments = payments
                .stream()
                .filter(payment -> payment.getSupplyId() == null)
                .map(payment -> nvlMoney(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal supplyPayments = supplies
                .stream()
                .filter(supply -> !"CANCELLED".equalsIgnoreCase(supply.getStatus()))
                .filter(supply -> !"VOID".equalsIgnoreCase(supply.getStatus()))
                .filter(supply -> !"VOIDED".equalsIgnoreCase(supply.getStatus()))
                .map(supply -> nvlMoney(supply.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return directPoPayments.add(supplyPayments);
    }

    private boolean matchesStatusFilter(String status, String filter) {
        if (!StringUtils.hasText(filter) || "ALL".equalsIgnoreCase(filter)) {
            return true;
        }

        String normalizedStatus = safe(status).toUpperCase();
        String normalizedFilter = filter.trim().toUpperCase();

        if ("OPEN".equals(normalizedFilter) || "PENDING".equals(normalizedFilter)) {
            return "OPEN".equals(normalizedStatus)
                    || "PENDING".equals(normalizedStatus)
                    || "PARTIAL".equals(normalizedStatus);
        }

        return normalizedStatus.equals(normalizedFilter);
    }

    private boolean matchesExactStatus(String status, String filter) {
        return !StringUtils.hasText(filter)
                || "ALL".equalsIgnoreCase(filter)
                || safe(status).equalsIgnoreCase(filter.trim());
    }

    private PurchaseOrderSummaryDto buildPurchaseOrderSummary(List<PurchaseOrderResponseDto> rows) {
        return PurchaseOrderSummaryDto.builder()
                .total(rows.size())
                .draft(rows.stream().filter(row -> "DRAFT".equalsIgnoreCase(row.getStatus())).count())
                .open(rows.stream().filter(row -> matchesStatusFilter(row.getStatus(), "OPEN")).count())
                .partiallyReceived(rows.stream().filter(row -> "PARTIALLY_RECEIVED".equalsIgnoreCase(row.getReceivingStatus())).count())
                .fullyReceived(rows.stream().filter(row -> "FULLY_RECEIVED".equalsIgnoreCase(row.getReceivingStatus())).count())
                .overdue(rows.stream().filter(row -> Boolean.TRUE.equals(row.getOverdue())).count())
                .build();
    }

    private Comparator<PurchaseOrderResponseDto> buildPurchaseOrderComparator(Pageable pageable) {
        List<Sort.Order> orders = pageable.getSort().stream().toList();
        if (orders.isEmpty()) {
            orders = List.of(Sort.Order.desc("createdAt"));
        }

        Comparator<PurchaseOrderResponseDto> comparator = null;
        for (Sort.Order order : orders) {
            Comparator<PurchaseOrderResponseDto> next = comparatorForPurchaseOrderSort(order.getProperty());
            if (order.isDescending()) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }

        return comparator != null ? comparator : comparatorForPurchaseOrderSort("createdAt").reversed();
    }

    private Comparator<PurchaseOrderResponseDto> comparatorForPurchaseOrderSort(String property) {
        return switch (property) {
            case "poNo" -> Comparator.comparing(row -> safe(row.getPoNo()), String.CASE_INSENSITIVE_ORDER);
            case "supplier", "supplierName" -> Comparator.comparing(row -> safe(row.getSupplierName()), String.CASE_INSENSITIVE_ORDER);
            case "expectedDate" -> Comparator.comparing(PurchaseOrderResponseDto::getExpectedDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "orderDate" -> Comparator.comparing(PurchaseOrderResponseDto::getOrderDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "totalAmount" -> Comparator.comparing(row -> nvlMoney(row.getTotalAmount()));
            case "receivingStatus" -> Comparator.comparing(row -> safe(row.getReceivingStatus()), String.CASE_INSENSITIVE_ORDER);
            case "paymentStatus" -> Comparator.comparing(row -> safe(row.getPaymentStatus()), String.CASE_INSENSITIVE_ORDER);
            case "status" -> Comparator.comparing(row -> safe(row.getStatus()), String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(PurchaseOrderResponseDto::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private String formatPageSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return "createdAt,DESC";
        }
        return String.join(";",
                pageable.getSort().stream()
                        .map(order -> order.getProperty() + "," + order.getDirection().name())
                        .toList());
    }

    private boolean isPurchaseOrderOverdue(LocalDate expectedDate, String receivingStatus, String poStatus) {
        if (expectedDate == null) {
            return false;
        }
        if ("CANCELLED".equalsIgnoreCase(poStatus) || "FULLY_RECEIVED".equalsIgnoreCase(receivingStatus)) {
            return false;
        }
        return expectedDate.isBefore(LocalDate.now());
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

        if (dto.getProducts() == null || dto.getProducts().isEmpty()) {
            throw new RuntimeException("Supply products cannot be empty");
        }

        validateDuplicateSupplyLines(dto.getProducts());
        validatePurchaseOrderCanReceive(dto);

        Long receivedBy = resolveUserId(dto.getReceivedBy());
        String status = dto.getStatus() != null ? dto.getStatus() : "COMPLETED";

        Supply existingSupply = findActiveSupplyForPurchaseOrder(dto.getPoId());
        boolean appendingToExistingGrn = existingSupply != null;

        if (!appendingToExistingGrn && dto.getGrnNo() != null && !dto.getGrnNo().isBlank()) {
            supplyRepository.findByGrnNo(dto.getGrnNo())
                    .ifPresent(x -> {
                        throw new RuntimeException("GRN number already exists");
                    });
        }

        Supply savedSupply;
        BigDecimal existingSubtotal = BigDecimal.ZERO;
        BigDecimal existingDiscount = BigDecimal.ZERO;
        BigDecimal existingTaxAmount = BigDecimal.ZERO;
        BigDecimal existingRounding = BigDecimal.ZERO;
        BigDecimal existingPaidAmount = BigDecimal.ZERO;

        if (appendingToExistingGrn) {
            if (!existingSupply.getBranchId().equals(dto.getBranchId())) {
                throw new RuntimeException("Existing GRN does not belong to this branch");
            }
            if (!existingSupply.getSupplierId().equals(dto.getSupplierId())) {
                throw new RuntimeException("Existing GRN does not belong to this supplier");
            }

            existingSubtotal = nvlMoney(existingSupply.getSubtotal());
            existingDiscount = nvlMoney(existingSupply.getDiscount());
            existingTaxAmount = nvlMoney(existingSupply.getTaxAmount());
            existingRounding = nvlMoney(existingSupply.getRounding());
            existingPaidAmount = nvlMoney(existingSupply.getPaidAmount());

            if (isBlank(existingSupply.getInvoiceNo()) && !isBlank(dto.getInvoiceNo())) {
                existingSupply.setInvoiceNo(dto.getInvoiceNo());
            }
            existingSupply.setPaymentMethod(normalizePaymentMethod(
                    dto.getPaymentMethod(),
                    normalizePaymentMethod(existingSupply.getPaymentMethod(), "CREDIT")
            ));
            existingSupply.setStatus(status);
            existingSupply.setReceivedBy(receivedBy);
            if (isBlank(existingSupply.getNotes()) && !isBlank(dto.getNotes())) {
                existingSupply.setNotes(dto.getNotes());
            }
            savedSupply = supplyRepository.save(existingSupply);
        } else {
            Supply supply = new Supply();
            supply.setBranchId(dto.getBranchId());
            supply.setSupplierId(dto.getSupplierId());
            supply.setPoId(dto.getPoId());
            supply.setGrnNo(resolveGrnNo(dto.getGrnNo()));
            supply.setInvoiceNo(dto.getInvoiceNo());
            supply.setSubtotal(BigDecimal.ZERO);
            supply.setDiscount(BigDecimal.ZERO);
            supply.setTaxAmount(BigDecimal.ZERO);
            supply.setRounding(BigDecimal.ZERO);
            supply.setTotal(BigDecimal.ZERO);
            supply.setPaidAmount(BigDecimal.ZERO);
            supply.setPayableAmount(BigDecimal.ZERO);
            supply.setBalanceAmount(BigDecimal.ZERO);
            supply.setPaymentStatus("UNPAID");
            supply.setPaymentMethod(normalizePaymentMethod(dto.getPaymentMethod(), "CREDIT"));
            supply.setStatus(status);
            supply.setSupplyDate(dto.getSupplyDate() != null ? dto.getSupplyDate() : LocalDateTime.now());
            supply.setReceivedBy(receivedBy);
            supply.setNotes(dto.getNotes());

            savedSupply = supplyRepository.save(supply);
        }

        BigDecimal calculatedSubtotal = BigDecimal.ZERO;

        for (SupplyProductRequestDto p : dto.getProducts()) {
            validateItemUnitAndVariant(dto.getBranchId(), p.getItemId(), p.getUnitId(), p.getVariantId());

            validateSupplierProvidesItem(
                    dto.getBranchId(),
                    dto.getSupplierId(),
                    p.getItemId(),
                    p.getVariantId(),
                    p.getUnitId()
            );

            BigDecimal qtyReceived = nvlQty(p.getQuantityReceived());
            BigDecimal qtyBase = unitConversionService.toBaseQty(p.getItemId(), p.getUnitId(), qtyReceived);
            BigDecimal costPrice = nvlMoney(p.getCostPrice());
            BigDecimal lineTotal = resolveSupplyRequestLineTotal(p, qtyReceived, costPrice);

            SupplyProduct product = new SupplyProduct();
            product.setSupplyId(savedSupply.getSupplyId());
            product.setItemId(p.getItemId());
            product.setVariantId(p.getVariantId());
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
                updateSupplierItemLastPurchaseCost(
                        dto.getBranchId(),
                        dto.getSupplierId(),
                        p.getItemId(),
                        p.getVariantId(),
                        p.getUnitId(),
                        savedProduct.getCostPrice()
                );
            }
        }
        refreshPurchaseOrderReceivedQtyFromSupplies(savedSupply.getPoId());
        updatePurchaseOrderStatusFromReceivedQty(savedSupply.getPoId());

        BigDecimal discount = nvlMoney(dto.getDiscount());
        BigDecimal taxAmount = nvlMoney(dto.getTaxAmount());
        BigDecimal rounding = nvlMoney(dto.getRounding());

        BigDecimal receiptTotal = calculatedSubtotal
                .subtract(discount)
                .add(taxAmount)
                .add(rounding)
                .max(BigDecimal.ZERO);

        BigDecimal paidAmount = nvlMoney(dto.getPaidAmount());
        BigDecimal receiptBalanceAmount = receiptTotal.subtract(paidAmount);

        if (receiptBalanceAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Paid amount cannot exceed total");
        }
        validatePurchaseOrderPaymentLimit(savedSupply.getPoId(), paidAmount);

        BigDecimal subtotal = existingSubtotal.add(calculatedSubtotal);
        BigDecimal totalDiscount = existingDiscount.add(discount);
        BigDecimal totalTaxAmount = existingTaxAmount.add(taxAmount);
        BigDecimal totalRounding = existingRounding.add(rounding);
        BigDecimal payableAmount = subtotal
                .subtract(totalDiscount)
                .add(totalTaxAmount)
                .add(totalRounding)
                .max(BigDecimal.ZERO);
        BigDecimal totalPaidAmount = existingPaidAmount.add(paidAmount);
        BigDecimal balanceAmount = payableAmount.subtract(totalPaidAmount);

        savedSupply.setSubtotal(subtotal);
        savedSupply.setDiscount(totalDiscount);
        savedSupply.setTaxAmount(totalTaxAmount);
        savedSupply.setRounding(totalRounding);
        savedSupply.setTotal(payableAmount);
        savedSupply.setPayableAmount(payableAmount);
        savedSupply.setPaidAmount(totalPaidAmount);
        savedSupply.setBalanceAmount(balanceAmount);
        savedSupply.setPaymentStatus(resolvePaymentStatus(payableAmount, totalPaidAmount));

        supplyRepository.save(savedSupply);
        createInitialSupplyPaymentIfNeeded(
                savedSupply,
                paidAmount,
                receivedBy,
                dto.getSupplyDate() != null ? dto.getSupplyDate() : LocalDateTime.now()
        );

        if ("COMPLETED".equalsIgnoreCase(status) && receiptBalanceAmount.compareTo(BigDecimal.ZERO) > 0) {
            supplier.setBalance(nvlMoney(supplier.getBalance()).add(receiptBalanceAmount));
            supplier.setUpdatedAt(LocalDateTime.now());
            supplierRepository.save(supplier);

            SupplierBalanceTransaction txn = new SupplierBalanceTransaction();
            txn.setBranchId(dto.getBranchId());
            txn.setSupplierId(dto.getSupplierId());
            txn.setType("SUPPLY_CREDIT");
            txn.setAmount(receiptBalanceAmount);
            txn.setRefTable("supplies");
            txn.setRefId(savedSupply.getSupplyId());
            txn.setNote(appendingToExistingGrn
                    ? "Balance added from additional received goods"
                    : "Balance added from completed supply");
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

        List<SupplyProduct> supplyProducts = supplyProductRepository.findBySupplyId(supplyId);
        BigDecimal calculatedSubtotal = supplyProducts.stream()
                .map(this::calculateSupplyProductLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal responseSubtotal = resolveStoredOrCalculatedMoney(supply.getSubtotal(), calculatedSubtotal);
        BigDecimal responseTotal = resolveStoredOrCalculatedMoney(
                supply.getTotal(),
                responseSubtotal
                        .subtract(nvlMoney(supply.getDiscount()))
                        .add(nvlMoney(supply.getTaxAmount()))
                        .add(nvlMoney(supply.getRounding()))
        );
        BigDecimal responsePayable = resolveStoredOrCalculatedMoney(supply.getPayableAmount(), responseTotal);
        BigDecimal responsePaid = nvlMoney(supply.getPaidAmount());
        BigDecimal responseBalance = resolveStoredOrCalculatedMoney(
                supply.getBalanceAmount(),
                responsePayable.subtract(responsePaid)
        );
        String responsePaymentStatus = responseTotal.compareTo(BigDecimal.ZERO) > 0
                ? resolvePaymentStatus(responsePayable, responsePaid)
                : supply.getPaymentStatus();

        if (shouldUsePurchaseOrderPaymentsForSupply(supply, responsePayable)) {
            BigDecimal purchaseOrderPaid = calculatePurchaseOrderPaidAmount(supply.getPoId());

            if (purchaseOrderPaid.compareTo(responsePaid) > 0) {
                responsePaid = purchaseOrderPaid.min(responsePayable);
                responseBalance = responsePayable.subtract(responsePaid).max(BigDecimal.ZERO);
                responsePaymentStatus = resolvePaymentStatus(responsePayable, responsePaid);
            }
        }

        List<SupplyProductResponseDto> products = supplyProducts
                .stream()
                .map(product -> {
                    ItemUnit unit = findItemUnit(product.getItemId(), product.getUnitId());
                    return SupplyProductResponseDto.builder()
                            .supplyProductId(product.getSupplyProductId())
                            .supplyId(product.getSupplyId())
                            .itemId(product.getItemId())
                            .variantId(product.getVariantId())
                            .variantSku(resolveVariantSku(product.getVariantId()))
                            .variantLabel(resolveVariantLabel(product.getVariantId()))
                            .unitId(product.getUnitId())
                            .masterUnitId(unit != null ? unit.getMasterUnitId() : null)
                            .unitName(resolveUnitName(unit))
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
                            .build();
                })
                .toList();

        return SupplyResponseDto.builder()
                .supplyId(supply.getSupplyId())
                .branchId(supply.getBranchId())
                .supplierId(supply.getSupplierId())
                .poId(supply.getPoId())
                .grnNo(supply.getGrnNo())
                .invoiceNo(supply.getInvoiceNo())
                .subtotal(responseSubtotal)
                .discount(supply.getDiscount())
                .taxAmount(supply.getTaxAmount())
                .rounding(supply.getRounding())
                .total(responseTotal)
                .paidAmount(responsePaid)
                .payableAmount(responsePayable)
                .balanceAmount(responseBalance)
                .paymentStatus(responsePaymentStatus)
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
    public List<SupplyResponseDto> getSuppliesByItem(Long branchId, Long itemId) {
        return supplyRepository.findByBranchIdAndItemId(branchId, itemId)
                .stream().map(s -> getSupplyById(s.getSupplyId())).toList();
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

        Set<String> supportedMethods = Set.of(
                "CASH", "COUNTER_CASH", "BANK_TRANSFER", "CHEQUE", "CARD", "ADVANCE_CREDIT");
        if (!supportedMethods.contains(paymentMethod)) {
            throw new RuntimeException("Unsupported supplier payment method: " + paymentMethod);
        }

        boolean usingAdvanceCredit = "ADVANCE_CREDIT".equals(paymentMethod);
        BigDecimal availableAdvanceCredit = nvlMoney(supplier.getAdvanceCredit());
        if (usingAdvanceCredit && dto.getAmount().compareTo(availableAdvanceCredit) > 0) {
            throw new RuntimeException("Payment amount exceeds supplier advance credit");
        }

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

            BigDecimal availableCash = calculateAvailableCash(cashSession);
            if (dto.getAmount().compareTo(availableCash) > 0) {
                throw new RuntimeException("Supplier cash payment exceeds available cash in drawer");
            }

            cashSessionId = cashSession.getSessionId();
        }

        Supply selectedSupply = resolvePaymentSupply(dto);
        List<Supply> targetSupplies;
        if (selectedSupply != null) {
            validatePaymentSupply(selectedSupply, dto.getBranchId(), dto.getSupplierId());
            targetSupplies = List.of(selectedSupply);
        } else {
            targetSupplies = supplyRepository
                    .findByBranchIdAndSupplierIdAndPaymentStatusNotOrderBySupplyDateAsc(
                            dto.getBranchId(), dto.getSupplierId(), "PAID")
                    .stream()
                    .filter(supply -> nvlMoney(supply.getBalanceAmount()).compareTo(BigDecimal.ZERO) > 0)
                    .toList();
        }

        BigDecimal totalOutstanding = targetSupplies.stream()
                .map(supply -> nvlMoney(supply.getBalanceAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (usingAdvanceCredit && dto.getAmount().compareTo(totalOutstanding) > 0) {
            throw new RuntimeException("Advance credit payment exceeds the selected outstanding GRN balance");
        }

        BigDecimal allocatedAmount = dto.getAmount().min(totalOutstanding);
        BigDecimal advanceCreditAdded = usingAdvanceCredit
                ? BigDecimal.ZERO : dto.getAmount().subtract(allocatedAmount);
        Long paymentPoId = selectedSupply != null && selectedSupply.getPoId() != null
                ? selectedSupply.getPoId() : dto.getPoId();

        SupplierPayment payment = new SupplierPayment();
        payment.setBranchId(dto.getBranchId());
        payment.setSupplierId(dto.getSupplierId());
        payment.setSupplyId(selectedSupply != null ? selectedSupply.getSupplyId() : null);
        payment.setPoId(paymentPoId);
        payment.setAmount(nvlMoney(dto.getAmount()));
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentDate(dto.getPaymentDate() != null ? dto.getPaymentDate() : LocalDateTime.now());
        payment.setPaidBy(paidBy);
        payment.setCashSessionId(cashSessionId);
        payment.setReferenceNo(dto.getReferenceNo());
        payment.setNote(dto.getNote());
        payment.setAllocatedAmount(allocatedAmount);
        payment.setAdvanceCreditAdded(advanceCreditAdded);
        payment.setAdvanceCreditUsed(usingAdvanceCredit ? allocatedAmount : BigDecimal.ZERO);

        SupplierPayment savedPayment = supplierPaymentRepository.save(payment);

        BigDecimal remainingToAllocate = allocatedAmount;
        for (Supply supply : targetSupplies) {
            if (remainingToAllocate.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal allocationAmount = remainingToAllocate.min(nvlMoney(supply.getBalanceAmount()));
            BigDecimal newPaid = nvlMoney(supply.getPaidAmount()).add(allocationAmount);
            BigDecimal total = calculateSupplyTotal(supply);
            supply.setPaidAmount(newPaid);
            supply.setBalanceAmount(total.subtract(newPaid));
            supply.setPaymentStatus(resolvePaymentStatus(total, newPaid));
            supplyRepository.save(supply);

            SupplierPaymentAllocation allocation = new SupplierPaymentAllocation();
            allocation.setSupplierPaymentId(savedPayment.getSupplierPaymentId());
            allocation.setSupplyId(supply.getSupplyId());
            allocation.setAmount(allocationAmount);
            supplierPaymentAllocationRepository.save(allocation);
            remainingToAllocate = remainingToAllocate.subtract(allocationAmount);
        }

        supplier.setBalance(nvlMoney(supplier.getBalance()).subtract(allocatedAmount).max(BigDecimal.ZERO));
        supplier.setAdvanceCredit(usingAdvanceCredit
                ? availableAdvanceCredit.subtract(allocatedAmount)
                : availableAdvanceCredit.add(advanceCreditAdded));
        supplier.setUpdatedAt(LocalDateTime.now());
        supplierRepository.save(supplier);

        if (allocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            SupplierBalanceTransaction txn = new SupplierBalanceTransaction();
            txn.setBranchId(dto.getBranchId());
            txn.setSupplierId(dto.getSupplierId());
            txn.setType("PAYMENT");
            txn.setAmount(allocatedAmount);
            txn.setRefTable("supplier_payments");
            txn.setRefId(savedPayment.getSupplierPaymentId());
            txn.setNote("Supplier payment allocated by " + paymentMethod);
            txn.setCreatedBy(paidBy);
            txn.setCreatedAt(LocalDateTime.now());
            supplierBalanceTransactionRepository.save(txn);
        }

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

        if (advanceCreditAdded.compareTo(BigDecimal.ZERO) > 0) {
            SupplierBalanceTransaction creditTxn = new SupplierBalanceTransaction();
            creditTxn.setBranchId(dto.getBranchId());
            creditTxn.setSupplierId(dto.getSupplierId());
            creditTxn.setType("ADVANCE_CREDIT_ADDED");
            creditTxn.setAmount(advanceCreditAdded);
            creditTxn.setRefTable("supplier_payments");
            creditTxn.setRefId(savedPayment.getSupplierPaymentId());
            creditTxn.setNote("Excess supplier payment stored as advance credit");
            creditTxn.setCreatedBy(paidBy);
            creditTxn.setCreatedAt(LocalDateTime.now());
            supplierBalanceTransactionRepository.save(creditTxn);
        } else if (usingAdvanceCredit) {
            SupplierBalanceTransaction creditTxn = new SupplierBalanceTransaction();
            creditTxn.setBranchId(dto.getBranchId());
            creditTxn.setSupplierId(dto.getSupplierId());
            creditTxn.setType("ADVANCE_CREDIT_USED");
            creditTxn.setAmount(allocatedAmount);
            creditTxn.setRefTable("supplier_payments");
            creditTxn.setRefId(savedPayment.getSupplierPaymentId());
            creditTxn.setNote("Supplier advance credit applied to GRN balance");
            creditTxn.setCreatedBy(paidBy);
            creditTxn.setCreatedAt(LocalDateTime.now());
            supplierBalanceTransactionRepository.save(creditTxn);
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

    @Override
    public List<SupplierPaymentResponseDto> getPaymentsByPurchaseOrder(Long poId) {
        return supplierPaymentRepository.findByPoId(poId)
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

        Stock stock = stockRepository.findByBranchIdAndItemIdAndVariantId(supply.getBranchId(), product.getItemId(), product.getVariantId())
                .orElseGet(() -> {
                    Stock s = new Stock();
                    s.setBranchId(supply.getBranchId());
                    s.setItemId(product.getItemId());
                    s.setVariantId(product.getVariantId());
                    s.setUnitId(baseUnit.getUnitId());
                    return s;
                });

        stock.setLastUpdated(LocalDateTime.now());
        stockRepository.save(stock);

        StockBatch batch = new StockBatch();
        batch.setBranchId(supply.getBranchId());
        batch.setItemId(product.getItemId());
        batch.setVariantId(product.getVariantId());
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
        movement.setVariantId(product.getVariantId());
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
                .findByPoIdAndItemIdAndVariantIdAndUnitId(poId, product.getItemId(), product.getVariantId(), product.getUnitId())
                .orElse(null);

        if (poItem != null) {
            poItem.setReceivedQty(nvlQty(poItem.getReceivedQty()).add(nvlQty(product.getQuantityReceived())));
            purchaseOrderItemRepository.save(poItem);
        }
    }

    private void updatePurchaseOrderStatusFromReceivedQty(Long poId) {
        if (poId == null) return;

        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(poId).orElse(null);
        if (purchaseOrder == null || "CANCELLED".equalsIgnoreCase(purchaseOrder.getStatus())) {
            return;
        }

        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPoId(poId);
        if (items.isEmpty()) {
            return;
        }

        BigDecimal receivedTotal = items.stream()
                .map(item -> nvlQty(item.getReceivedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String nextStatus;
        if (receivedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            nextStatus = "PENDING";
        } else {
            boolean fullyReceived = items.stream()
                    .allMatch(item -> nvlQty(item.getReceivedQty()).compareTo(nvlQty(item.getOrderedQty())) >= 0);
            nextStatus = fullyReceived ? "COMPLETED" : "PARTIAL";
        }

        purchaseOrder.setStatus(nextStatus);
        purchaseOrderRepository.save(purchaseOrder);
    }

    private void refreshPurchaseOrderReceivedQtyFromSupplies(Long poId) {
        if (poId == null) return;

        List<PurchaseOrderItem> poItems = purchaseOrderItemRepository.findByPoId(poId);
        if (poItems.isEmpty()) {
            return;
        }

        Map<String, BigDecimal> receivedByLine = new LinkedHashMap<>();
        List<Supply> supplies = supplyRepository.findByPoId(poId);
        if (supplies == null) {
            supplies = List.of();
        }

        for (Supply supply : supplies) {
            if (!isSupplyCountedForReceiving(supply)) {
                continue;
            }

            for (SupplyProduct product : supplyProductRepository.findBySupplyId(supply.getSupplyId())) {
                String key = poLineKey(product.getItemId(), product.getVariantId(), product.getUnitId());
                receivedByLine.merge(key, nvlQty(product.getQuantityReceived()), BigDecimal::add);
            }
        }

        for (PurchaseOrderItem item : poItems) {
            item.setReceivedQty(receivedByLine.getOrDefault(
                    poLineKey(item.getItemId(), item.getVariantId(), item.getUnitId()),
                    BigDecimal.ZERO
            ));
            purchaseOrderItemRepository.save(item);
        }
    }

    private boolean isSupplyCountedForReceiving(Supply supply) {
        String status = supply.getStatus();
        return status != null
                && "COMPLETED".equalsIgnoreCase(status)
                && !"CANCELLED".equalsIgnoreCase(status)
                && !"VOID".equalsIgnoreCase(status)
                && !"VOIDED".equalsIgnoreCase(status);
    }

    private BigDecimal calculatePurchaseOrderPaidAmount(Long poId) {
        if (poId == null) {
            return BigDecimal.ZERO;
        }

        List<SupplierPayment> directPayments = supplierPaymentRepository.findByPoId(poId);
        if (directPayments == null) {
            directPayments = List.of();
        }

        List<Supply> supplies = supplyRepository.findByPoId(poId);
        if (supplies == null) {
            supplies = List.of();
        }

        BigDecimal directPoPayments = directPayments
                .stream()
                .filter(payment -> payment.getSupplyId() == null)
                .map(payment -> nvlMoney(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal supplyPayments = supplies
                .stream()
                .filter(supply -> !"CANCELLED".equalsIgnoreCase(supply.getStatus()))
                .filter(supply -> !"VOID".equalsIgnoreCase(supply.getStatus()))
                .filter(supply -> !"VOIDED".equalsIgnoreCase(supply.getStatus()))
                .map(supply -> nvlMoney(supply.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return directPoPayments.add(supplyPayments);
    }

    private BigDecimal calculatePurchaseOrderTotal(Long poId) {
        if (poId == null) {
            return BigDecimal.ZERO;
        }

        return purchaseOrderItemRepository.findByPoId(poId)
                .stream()
                .map(item -> nvlQty(item.getOrderedQty()).multiply(nvlMoney(item.getUnitCostEst())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean shouldUsePurchaseOrderPaymentsForSupply(Supply supply, BigDecimal supplyPayable) {
        if (supply == null || supply.getPoId() == null || supply.getSupplyId() == null) {
            return false;
        }

        BigDecimal purchaseOrderTotal = calculatePurchaseOrderTotal(supply.getPoId());
        if (purchaseOrderTotal.compareTo(nvlMoney(supplyPayable)) != 0) {
            return false;
        }

        List<Supply> linkedSupplies = supplyRepository.findByPoId(supply.getPoId());
        if (linkedSupplies == null) {
            return false;
        }

        List<Supply> activeSupplies = linkedSupplies.stream()
                .filter(this::isSupplyActiveForPaymentReconciliation)
                .toList();

        return activeSupplies.size() == 1
                && activeSupplies.get(0).getSupplyId().equals(supply.getSupplyId());
    }

    private boolean isSupplyActiveForPaymentReconciliation(Supply supply) {
        if (supply == null) {
            return false;
        }

        String status = supply.getStatus();
        return status == null
                || (!"CANCELLED".equalsIgnoreCase(status)
                    && !"VOID".equalsIgnoreCase(status)
                    && !"VOIDED".equalsIgnoreCase(status));
    }

    private Supply findActiveSupplyForPurchaseOrder(Long poId) {
        if (poId == null) {
            return null;
        }

        List<Supply> supplies = supplyRepository.findByPoId(poId);
        if (supplies == null || supplies.isEmpty()) {
            return null;
        }

        return supplies.stream()
                .filter(this::isSupplyActiveForPaymentReconciliation)
                .min(Comparator
                        .comparing(
                                Supply::getSupplyDate,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(
                                Supply::getSupplyId,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                .orElse(null);
    }

    private String resolvePurchaseOrderReceivingStatus(
            List<PurchaseOrderItemResponseDto> items,
            String orderStatus
    ) {
        if ("CANCELLED".equalsIgnoreCase(orderStatus)) {
            return "CANCELLED";
        }

        if (items == null || items.isEmpty()) {
            return "NOT_RECEIVED";
        }

        BigDecimal receivedTotal = items.stream()
                .map(item -> nvlQty(item.getReceivedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (receivedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return "NOT_RECEIVED";
        }

        boolean fullyReceived = items.stream()
                .allMatch(item -> nvlQty(item.getReceivedQty()).compareTo(nvlQty(item.getOrderedQty())) >= 0);

        return fullyReceived ? "FULLY_RECEIVED" : "PARTIALLY_RECEIVED";
    }

    // =========================
    // VALIDATION HELPERS
    // =========================

    private void validatePurchaseOrderCanReceive(SupplyRequestDto dto) {
        if (dto.getPoId() == null) {
            return;
        }

        refreshPurchaseOrderReceivedQtyFromSupplies(dto.getPoId());

        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(dto.getPoId())
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));

        if (!purchaseOrder.getBranchId().equals(dto.getBranchId())) {
            throw new RuntimeException("Purchase order does not belong to this branch");
        }

        if (!purchaseOrder.getSupplierId().equals(dto.getSupplierId())) {
            throw new RuntimeException("Purchase order does not belong to this supplier");
        }

        if ("CANCELLED".equalsIgnoreCase(purchaseOrder.getStatus())) {
            throw new RuntimeException("Cannot receive goods against a cancelled purchase order");
        }

        Map<String, BigDecimal> requestQtyByLine = new LinkedHashMap<>();
        for (SupplyProductRequestDto product : dto.getProducts()) {
            BigDecimal quantityReceived = nvlQty(product.getQuantityReceived());
            if (quantityReceived.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Received quantity must be greater than zero");
            }

            String key = poLineKey(product.getItemId(), product.getVariantId(), product.getUnitId());
            requestQtyByLine.merge(key, quantityReceived, BigDecimal::add);
        }

        for (Map.Entry<String, BigDecimal> entry : requestQtyByLine.entrySet()) {
            String[] keyParts = entry.getKey().split("-");
            Long itemId = Long.valueOf(keyParts[0]);
            Long parsedVariantId = Long.valueOf(keyParts[1]);
            Long variantId = parsedVariantId == 0L ? null : parsedVariantId;
            Long unitId = Long.valueOf(keyParts[2]);

            PurchaseOrderItem poItem = purchaseOrderItemRepository
                    .findByPoIdAndItemIdAndVariantIdAndUnitId(dto.getPoId(), itemId, variantId, unitId)
                    .orElseThrow(() -> new RuntimeException(
                            "Received item/variant/unit is not on the purchase order: itemId="
                                    + itemId + ", variantId=" + variantId + ", unitId=" + unitId
                    ));

            BigDecimal remainingQty = nvlQty(poItem.getOrderedQty())
                    .subtract(nvlQty(poItem.getReceivedQty()));

            if (entry.getValue().compareTo(remainingQty) > 0) {
                throw new RuntimeException(
                        "Received quantity exceeds remaining purchase order quantity for itemId="
                                + itemId + ", variantId=" + variantId + ", unitId=" + unitId
                );
            }
        }
    }

    private void validatePurchaseOrderPaymentLimit(Long poId, BigDecimal paymentAmount) {
        if (poId == null) {
            return;
        }

        BigDecimal amount = nvlMoney(paymentAmount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal poTotal = calculatePurchaseOrderTotal(poId);
        BigDecimal paidAmount = calculatePurchaseOrderPaidAmount(poId);

        if (paidAmount.add(amount).compareTo(poTotal) > 0) {
            throw new RuntimeException("Payment amount exceeds purchase order balance");
        }
    }

    private String poLineKey(Long itemId, Long variantId, Long unitId) {
        return itemId + "-" + (variantId != null ? variantId : 0L) + "-" + unitId;
    }

    private void validateItemUnitAndVariant(Long branchId, Long itemId, Long unitId, Long variantId) {
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

    private void validateSupplierProvidesItem(Long branchId, Long supplierId, Long itemId, Long variantId, Long unitId) {
        SupplierItem supplierItem = supplierItemRepository
                .findByBranchIdAndSupplierIdAndItemIdAndVariantIdAndUnitId(branchId, supplierId, itemId, variantId, unitId)
                .orElseThrow(() -> new RuntimeException(
                        "Supplier does not supply itemId=" + itemId + ", variantId=" + variantId + " with unitId=" + unitId
                ));

        if (!Boolean.TRUE.equals(supplierItem.getIsActive())) {
            throw new RuntimeException(
                    "Supplier item mapping is inactive for itemId=" + itemId + ", variantId=" + variantId + " with unitId=" + unitId
            );
        }
    }

    private void updateSupplierItemLastPurchaseCost(
            Long branchId,
            Long supplierId,
            Long itemId,
            Long variantId,
            Long unitId,
            BigDecimal costPrice
    ) {
        supplierItemRepository
                .findByBranchIdAndSupplierIdAndItemIdAndVariantIdAndUnitId(branchId, supplierId, itemId, variantId, unitId)
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

    private List<PurchaseOrderItemRequestDto> normalizePurchaseOrderItems(List<PurchaseOrderItemRequestDto> items) {
        Map<String, PurchaseOrderItemRequestDto> mergedItems = new LinkedHashMap<>();
        Map<String, BigDecimal> mergedLineTotals = new LinkedHashMap<>();

        for (PurchaseOrderItemRequestDto item : items) {
            if (item.getItemId() == null || item.getUnitId() == null) {
                throw new RuntimeException("PO item and unit are required");
            }

            String key = poLineKey(item.getItemId(), item.getVariantId(), item.getUnitId());
            BigDecimal qty = nvlQty(item.getOrderedQty());
            BigDecimal unitCost = nvlMoney(item.getUnitCostEst());
            BigDecimal lineTotal = qty.multiply(unitCost);

            PurchaseOrderItemRequestDto existing = mergedItems.get(key);
            if (existing == null) {
                PurchaseOrderItemRequestDto copy = new PurchaseOrderItemRequestDto();
                copy.setItemId(item.getItemId());
                copy.setVariantId(item.getVariantId());
                copy.setUnitId(item.getUnitId());
                copy.setOrderedQty(qty);
                copy.setUnitCostEst(unitCost);
                mergedItems.put(key, copy);
                mergedLineTotals.put(key, lineTotal);
                continue;
            }

            BigDecimal mergedQty = nvlQty(existing.getOrderedQty()).add(qty);
            BigDecimal mergedLineTotal = mergedLineTotals.get(key).add(lineTotal);

            existing.setOrderedQty(mergedQty);
            existing.setUnitCostEst(mergedQty.compareTo(BigDecimal.ZERO) > 0
                    ? mergedLineTotal.divide(mergedQty, 4, RoundingMode.HALF_UP)
                    : unitCost);
            mergedLineTotals.put(key, mergedLineTotal);
        }

        return new ArrayList<>(mergedItems.values());
    }

    private BigDecimal resolveSupplyRequestLineTotal(
            SupplyProductRequestDto product,
            BigDecimal qtyReceived,
            BigDecimal costPrice
    ) {
        if (product.getLineTotal() != null && product.getLineTotal().compareTo(BigDecimal.ZERO) > 0) {
            return product.getLineTotal();
        }

        return costPrice.multiply(qtyReceived);
    }

    private BigDecimal calculateSupplyTotal(Supply supply) {
        BigDecimal storedTotal = nvlMoney(supply.getTotal());
        if (storedTotal.compareTo(BigDecimal.ZERO) > 0) {
            return storedTotal;
        }

        BigDecimal calculatedSubtotal = supplyProductRepository.findBySupplyId(supply.getSupplyId())
                .stream()
                .map(this::calculateSupplyProductLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return calculatedSubtotal
                .subtract(nvlMoney(supply.getDiscount()))
                .add(nvlMoney(supply.getTaxAmount()))
                .add(nvlMoney(supply.getRounding()));
    }

    private BigDecimal calculateSupplyProductLineTotal(SupplyProduct product) {
        BigDecimal storedLineTotal = nvlMoney(product.getLineTotal());
        if (storedLineTotal.compareTo(BigDecimal.ZERO) > 0) {
            return storedLineTotal;
        }

        return nvlMoney(product.getCostPrice()).multiply(nvlQty(product.getQuantityReceived()));
    }

    private BigDecimal resolveStoredOrCalculatedMoney(BigDecimal stored, BigDecimal calculated) {
        BigDecimal storedMoney = nvlMoney(stored);
        if (storedMoney.compareTo(BigDecimal.ZERO) > 0) {
            return storedMoney;
        }

        return nvlMoney(calculated);
    }

    private void validateDuplicateSupplyLines(List<SupplyProductRequestDto> products) {
        Set<String> keys = new HashSet<>();

        for (SupplyProductRequestDto p : products) {
            String key = p.getItemId()
                    + "-"
                    + (p.getVariantId() != null ? p.getVariantId() : 0L)
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

    private BigDecimal calculateAvailableCash(CashSession cashSession) {
        Long sessionId = cashSession.getSessionId();
        List<CashSessionTransaction> txns = cashSessionTransactionRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);

        BigDecimal cashSales = calculateCashSales(sessionId, txns);
        BigDecimal expenses = sumCashTransactionsByType(txns, "EXPENSE");
        BigDecimal withdrawals = sumCashTransactionsByType(txns, "WITHDRAWAL");
        BigDecimal cashRefunds = sumCashTransactionsByTypeAndCashMethod(txns, "REFUND");
        BigDecimal customerPayments = sumCashTransactionsByTypes(txns, "CUSTOMER_PAYMENT_IN");
        BigDecimal supplierPayments = sumCashTransactionsByTypes(txns, "SUPPLIER_PAYMENT", "SUPPLIER_PAYMENT_OUT");
        BigDecimal supplierRefunds = sumCashTransactionsByTypes(txns, "SUPPLIER_REFUND_IN", "PURCHASE_RETURN_CASH_REFUND");

        return nvlMoney(cashSession.getOpeningCash())
                .add(cashSales)
                .add(customerPayments)
                .add(supplierRefunds)
                .subtract(expenses)
                .subtract(withdrawals)
                .subtract(cashRefunds)
                .subtract(supplierPayments)
                .max(BigDecimal.ZERO);
    }

    private BigDecimal calculateCashSales(Long sessionId, List<CashSessionTransaction> txns) {
        List<CustomerOrder> orders = orderRepository.findByCashSessionIdOrderByOrderDateDesc(sessionId)
                .stream()
                .filter(order -> "COMPLETED".equalsIgnoreCase(order.getStatus()))
                .toList();

        Set<Long> orderIds = new HashSet<>();
        Set<String> invoiceNos = new HashSet<>();

        BigDecimal reconciledCashSales = orders.stream()
                .peek(order -> {
                    if (order.getOrderId() != null) orderIds.add(order.getOrderId());
                    if (order.getInvoiceNo() != null) invoiceNos.add(order.getInvoiceNo());
                })
                .map(order -> calculateOrderCashSale(sessionId, order))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal legacyCashSales = txns.stream()
                .filter(t -> "SALE".equals(t.getType()) && isCashPayment(t.getPaymentMethod()))
                .filter(t -> t.getOrderId() == null || !orderIds.contains(t.getOrderId()))
                .filter(t -> t.getInvoiceNo() == null || !invoiceNos.contains(t.getInvoiceNo()))
                .map(t -> nvlMoney(t.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return reconciledCashSales.add(legacyCashSales);
    }

    private BigDecimal calculateOrderCashSale(Long sessionId, CustomerOrder order) {
        List<Payment> payments = paymentRepository.findByOrderId(order.getOrderId());
        BigDecimal cashPaid = payments.stream()
                .filter(payment -> payment.getCashSessionId() == null || payment.getCashSessionId().equals(sessionId))
                .filter(payment -> isCashPayment(payment.getPaymentMethod()))
                .map(payment -> nvlMoney(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal nonCashPaid = payments.stream()
                .filter(payment -> !isCashPayment(payment.getPaymentMethod()))
                .map(payment -> nvlMoney(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashShareOfInvoice = nvlMoney(order.getTotal()).subtract(nonCashPaid).max(BigDecimal.ZERO);
        return cashPaid.min(cashShareOfInvoice);
    }

    private boolean isCashPayment(String paymentMethod) {
        return "CASH".equalsIgnoreCase(paymentMethod)
                || "COUNTER_CASH".equalsIgnoreCase(paymentMethod);
    }

    private BigDecimal sumCashTransactionsByType(List<CashSessionTransaction> txns, String type) {
        return txns.stream()
                .filter(t -> type.equals(t.getType()))
                .map(t -> nvlMoney(t.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumCashTransactionsByTypeAndCashMethod(List<CashSessionTransaction> txns, String type) {
        return txns.stream()
                .filter(t -> type.equals(t.getType()) && isCashPayment(t.getPaymentMethod()))
                .map(t -> nvlMoney(t.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumCashTransactionsByTypes(List<CashSessionTransaction> txns, String... types) {
        Set<String> included = Set.of(types);
        return txns.stream()
                .filter(t -> included.contains(t.getType()))
                .map(t -> nvlMoney(t.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean requiresCashSession(String paymentMethod) {
        if (paymentMethod == null) return false;

        return paymentMethod.equalsIgnoreCase("CASH")
                || paymentMethod.equalsIgnoreCase("COUNTER_CASH")
                || paymentMethod.equalsIgnoreCase("COUNTER_PAYMENT");
    }

    private Supply resolvePaymentSupply(SupplierPaymentRequestDto dto) {
        Supply byId = dto.getSupplyId() == null ? null : supplyRepository.findById(dto.getSupplyId())
                .orElseThrow(() -> new RuntimeException("Supply not found"));
        Supply byGrn = isBlank(dto.getGrnNo()) ? null : supplyRepository.findByGrnNo(dto.getGrnNo().trim())
                .orElseThrow(() -> new RuntimeException("GRN not found: " + dto.getGrnNo()));
        if (byId != null && byGrn != null && !byId.getSupplyId().equals(byGrn.getSupplyId())) {
            throw new RuntimeException("supplyId and grnNo refer to different GRNs");
        }
        return byId != null ? byId : byGrn;
    }

    private void validatePaymentSupply(Supply supply, Long branchId, Long supplierId) {
        if (!supply.getBranchId().equals(branchId)) {
            throw new RuntimeException("Supply does not belong to this branch");
        }
        if (!supply.getSupplierId().equals(supplierId)) {
            throw new RuntimeException("Supply does not belong to this supplier");
        }
        if (nvlMoney(supply.getBalanceAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("GRN is already fully paid");
        }
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

    private String normalizePaymentMethod(String paymentMethod, String fallback) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return fallback;
        }

        return paymentMethod.trim().toUpperCase();
    }

    private void createInitialSupplyPaymentIfNeeded(Supply supply, BigDecimal paidAmount, Long paidBy) {
        createInitialSupplyPaymentIfNeeded(
                supply,
                paidAmount,
                paidBy,
                supply.getSupplyDate() != null ? supply.getSupplyDate() : LocalDateTime.now()
        );
    }

    private void createInitialSupplyPaymentIfNeeded(
            Supply supply,
            BigDecimal paidAmount,
            Long paidBy,
            LocalDateTime paymentDate
    ) {
        BigDecimal amount = nvlMoney(paidAmount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        SupplierPayment payment = new SupplierPayment();
        payment.setBranchId(supply.getBranchId());
        payment.setSupplierId(supply.getSupplierId());
        payment.setSupplyId(supply.getSupplyId());
        payment.setPoId(supply.getPoId());
        payment.setAmount(amount);
        payment.setPaymentMethod(normalizePaymentMethod(supply.getPaymentMethod(), "CASH"));
        payment.setPaymentDate(paymentDate != null ? paymentDate : LocalDateTime.now());
        payment.setPaidBy(resolveUserId(paidBy));
        payment.setReferenceNo(supply.getInvoiceNo());
        payment.setNote("Initial payment recorded during GRN");

        supplierPaymentRepository.save(payment);
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

    purchaseReturn.setBranchId(dto.getBranchId());
    purchaseReturn.setSupplierId(dto.getSupplierId());
    purchaseReturn.setSupplyId(dto.getSupplyId());
    purchaseReturn.setReturnDate(dto.getReturnDate() != null ? dto.getReturnDate() : LocalDateTime.now());
    purchaseReturn.setRefundMethod(null);
    purchaseReturn.setCashSessionId(null);
    purchaseReturn.setBankReference(null);
    purchaseReturn.setRefundAmount(BigDecimal.ZERO);
    purchaseReturn.setPaidAmount(BigDecimal.ZERO);
    purchaseReturn.setBalanceDue(BigDecimal.ZERO);
    purchaseReturn.setPaymentStatus("UNPAID");
    purchaseReturn.setReason(dto.getReason());
    purchaseReturn.setProcessedBy(processedBy);
    purchaseReturn.setStatus("COMPLETED");

    PurchaseReturn savedReturn = purchaseReturnRepository.save(purchaseReturn);

    for (PurchaseReturnItemRequestDto itemDto : dto.getItems()) {

        Long variantId = resolveBatchVariantId(dto.getBranchId(), itemDto.getItemId(), itemDto.getVariantId(), itemDto.getInternalBatchBarcode());
        itemDto.setVariantId(variantId);
        validateItemUnitAndVariant(dto.getBranchId(), itemDto.getItemId(), itemDto.getUnitId(), variantId);

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
        returnItem.setVariantId(itemDto.getVariantId());
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
    savedReturn.setPaidAmount(BigDecimal.ZERO);
    savedReturn.setBalanceDue(refundAmount);
    savedReturn.setPaymentStatus("UNPAID");
    savedReturn.setReturnNo("PR-" + savedReturn.getPurchaseReturnId());
    purchaseReturnRepository.save(savedReturn);

    return getPurchaseReturnById(savedReturn.getPurchaseReturnId());
}

@Override
public PurchaseReturnRefundResponseDto recordPurchaseReturnRefund(
        Long purchaseReturnId, PurchaseReturnRefundRequestDto dto) {
    PurchaseReturn purchaseReturn = purchaseReturnRepository.findById(purchaseReturnId)
            .orElseThrow(() -> new RuntimeException("Purchase return not found"));

    if (dto == null || dto.getBranchId() == null || !dto.getBranchId().equals(purchaseReturn.getBranchId())) {
        throw new RuntimeException("Purchase return does not belong to this branch");
    }

    if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new RuntimeException("Refund amount must be greater than 0");
    }

    String method = dto.getRefundMethod() == null ? "" : dto.getRefundMethod().trim().toUpperCase();
    if (!Set.of("CASH_REFUND", "BANK_REFUND", "BALANCE_ADJUSTMENT").contains(method)) {
        throw new RuntimeException("Refund method must be CASH_REFUND, BANK_REFUND, or BALANCE_ADJUSTMENT");
    }
    if ("BANK_REFUND".equals(method) && isBlank(dto.getBankReference())) {
        throw new RuntimeException("Bank reference is required for bank refund");
    }

    BigDecimal refundTotal = nvlMoney(purchaseReturn.getRefundAmount());
    BigDecimal paidAmount = nvlMoney(purchaseReturn.getPaidAmount());
    BigDecimal balanceDue = refundTotal.subtract(paidAmount);
    if (dto.getAmount().compareTo(balanceDue) > 0) {
        throw new RuntimeException("Refund amount cannot exceed balance due: " + balanceDue);
    }

    Long counterId = null;
    Long cashSessionId = null;
    if ("CASH_REFUND".equals(method)) {
        if (dto.getCashSessionId() == null) {
            throw new RuntimeException("Cash session is required for cash refund");
        }
        CashSession session = cashSessionRepository.findById(dto.getCashSessionId())
                .orElseThrow(() -> new RuntimeException("Cash session not found"));
        if (!"OPEN".equalsIgnoreCase(session.getStatus())) {
            throw new RuntimeException("Cash session is not OPEN");
        }
        if (dto.getCounterId() != null && !dto.getCounterId().equals(session.getCounterId())) {
            throw new RuntimeException("Cash session does not belong to the selected counter");
        }
        Counter counter = counterRepository.findById(session.getCounterId())
                .orElseThrow(() -> new RuntimeException("Counter not found"));
        if (!purchaseReturn.getBranchId().equals(counter.getBranchId())) {
            throw new RuntimeException("Cash session counter does not belong to this branch");
        }
        counterId = counter.getCounterId();
        cashSessionId = session.getSessionId();
    }

    Long processedBy = resolveUserId(dto.getProcessedBy());
    LocalDateTime now = LocalDateTime.now();
    PurchaseReturnRefund refund = new PurchaseReturnRefund();
    refund.setPurchaseReturnId(purchaseReturnId);
    refund.setBranchId(purchaseReturn.getBranchId());
    refund.setAmount(dto.getAmount());
    refund.setRefundMethod(method);
    refund.setReferenceNo(clean(dto.getReferenceNo()));
    refund.setBankReference(clean(dto.getBankReference()));
    refund.setCounterId(counterId);
    refund.setCashSessionId(cashSessionId);
    refund.setProcessedBy(processedBy);
    refund.setRefundedAt(now);
    PurchaseReturnRefund saved = purchaseReturnRefundRepository.save(refund);

    BigDecimal newPaidAmount = paidAmount.add(dto.getAmount());
    BigDecimal newBalanceDue = refundTotal.subtract(newPaidAmount);
    purchaseReturn.setPaidAmount(newPaidAmount);
    purchaseReturn.setBalanceDue(newBalanceDue);
    purchaseReturn.setPaymentStatus(newBalanceDue.compareTo(BigDecimal.ZERO) == 0
            ? "REFUNDED" : "PARTIALLY_REFUNDED");
    purchaseReturnRepository.save(purchaseReturn);

    Supplier supplier = supplierRepository.findById(purchaseReturn.getSupplierId())
            .orElseThrow(() -> new RuntimeException("Supplier not found"));
    supplier.setBalance(nvlMoney(supplier.getBalance()).subtract(dto.getAmount()));
    supplier.setUpdatedAt(now);
    supplierRepository.save(supplier);

    SupplierBalanceTransaction ledgerTxn = new SupplierBalanceTransaction();
    ledgerTxn.setBranchId(purchaseReturn.getBranchId());
    ledgerTxn.setSupplierId(purchaseReturn.getSupplierId());
    ledgerTxn.setType("PURCHASE_RETURN_REFUND");
    ledgerTxn.setAmount(dto.getAmount());
    ledgerTxn.setRefTable("purchase_return_refunds");
    ledgerTxn.setRefId(saved.getRefundId());
    ledgerTxn.setNote("Supplier refund for " + purchaseReturn.getReturnNo() + " via " + method);
    ledgerTxn.setCreatedBy(processedBy);
    ledgerTxn.setCreatedAt(now);
    supplierBalanceTransactionRepository.save(ledgerTxn);

    if ("CASH_REFUND".equals(method)) {
        CashSessionTransaction cashTxn = new CashSessionTransaction();
        cashTxn.setSessionId(cashSessionId);
        cashTxn.setType("PURCHASE_RETURN_CASH_REFUND");
        cashTxn.setAmount(dto.getAmount());
        cashTxn.setPaymentMethod("CASH");
        cashTxn.setPurchaseReturnId(purchaseReturnId);
        cashTxn.setSupplierId(purchaseReturn.getSupplierId());
        cashTxn.setSupplyId(purchaseReturn.getSupplyId());
        cashTxn.setCounterId(counterId);
        cashTxn.setReferenceNo(clean(dto.getReferenceNo()));
        cashTxn.setNote(buildPurchaseReturnCashRefundNote(purchaseReturn, supplier));
        cashTxn.setCreatedBy(processedBy);
        cashTxn.setCreatedAt(now);
        cashSessionTransactionRepository.save(cashTxn);
    }

    return mapPurchaseReturnRefund(saved);
}

@Override
public List<PurchaseReturnRefundResponseDto> getPurchaseReturnRefunds(Long purchaseReturnId) {
    if (!purchaseReturnRepository.existsById(purchaseReturnId)) {
        throw new RuntimeException("Purchase return not found");
    }
    return purchaseReturnRefundRepository.findByPurchaseReturnIdOrderByRefundedAtDesc(purchaseReturnId)
            .stream().map(this::mapPurchaseReturnRefund).toList();
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

    Long variantId = resolveBatchVariantId(dto.getBranchId(), itemDto.getItemId(), itemDto.getVariantId(), itemDto.getInternalBatchBarcode());
    Stock stock = stockRepository.findByBranchIdAndItemIdAndVariantId(dto.getBranchId(), itemDto.getItemId(), variantId)
            .orElseThrow(() -> new RuntimeException("Stock not found"));

    switch (type) {
        case "DAMAGED" -> {
            if (getBatchQtyTotal(dto.getBranchId(), itemDto.getItemId(), variantId, StockQtyType.DAMAGED).compareTo(qtyBase) < 0) {
                throw new RuntimeException("Not enough damaged stock to return");
            }
        }

        case "EXPIRED" -> {
            if (getBatchQtyTotal(dto.getBranchId(), itemDto.getItemId(), variantId, StockQtyType.EXPIRED).compareTo(qtyBase) < 0) {
                throw new RuntimeException("Not enough expired stock to return");
            }
        }

        default -> {
            if (getBatchQtyTotal(dto.getBranchId(), itemDto.getItemId(), variantId, StockQtyType.AVAILABLE).compareTo(qtyBase) < 0) {
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

        if (!java.util.Objects.equals(batch.getVariantId(), variantId)) {
            throw new RuntimeException("Batch does not belong to selected variant");
        }

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
        deductBatchQtyFifo(dto.getBranchId(), itemDto.getItemId(), variantId, qtyBase, type);
    }

    StockMovement movement = new StockMovement();
    movement.setBranchId(dto.getBranchId());
    movement.setMovementType("PURCHASE_RETURN_OUT");
    movement.setItemId(itemDto.getItemId());
    movement.setVariantId(variantId);
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
                .advanceCredit(nvlMoney(supplier.getAdvanceCredit()))
                .isActive(supplier.getIsActive())
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }

    private SupplierItemResponseDto mapSupplierItem(SupplierItem supplierItem) {
        Item item = itemRepository.findById(supplierItem.getItemId()).orElse(null);
        ItemUnit unit = itemUnitRepository
                .findByItemIdAndUnitIdAndIsActiveTrue(supplierItem.getItemId(), supplierItem.getUnitId())
                .orElse(null);
        Supplier supplier = supplierRepository.findById(supplierItem.getSupplierId()).orElse(null);

        return SupplierItemResponseDto.builder()
                .supplierItemId(supplierItem.getSupplierItemId())
                .branchId(supplierItem.getBranchId())
                .supplierId(supplierItem.getSupplierId())
                .supplierName(supplier != null ? supplier.getName() : null)
                .itemId(supplierItem.getItemId())
                .variantId(supplierItem.getVariantId())
                .variantSku(resolveVariantSku(supplierItem.getVariantId()))
                .variantLabel(resolveVariantLabel(supplierItem.getVariantId()))
                .itemName(item != null ? item.getName() : null)
                .sku(item != null ? item.getSku() : null)
                .unitId(supplierItem.getUnitId())
                .masterUnitId(unit != null ? unit.getMasterUnitId() : null)
                .unitName(resolveUnitName(unit))
                .lastPurchaseCost(supplierItem.getLastPurchaseCost())
                .defaultCostPrice(supplierItem.getDefaultCostPrice())
                .isPreferred(supplierItem.getIsPreferred())
                .isActive(supplierItem.getIsActive())
                .createdAt(supplierItem.getCreatedAt())
                .updatedAt(supplierItem.getUpdatedAt())
                .build();
    }

    private SupplierPaymentResponseDto mapSupplierPayment(SupplierPayment payment) {
        List<SupplierPaymentAllocationResponseDto> allocations = supplierPaymentAllocationRepository
                .findBySupplierPaymentIdOrderByAllocationIdAsc(payment.getSupplierPaymentId())
                .stream()
                .map(allocation -> {
                    Supply supply = supplyRepository.findById(allocation.getSupplyId()).orElse(null);
                    return SupplierPaymentAllocationResponseDto.builder()
                            .allocationId(allocation.getAllocationId())
                            .supplyId(allocation.getSupplyId())
                            .grnNo(supply != null ? supply.getGrnNo() : null)
                            .amount(allocation.getAmount())
                            .paidAmount(supply != null ? supply.getPaidAmount() : null)
                            .balanceAmount(supply != null ? supply.getBalanceAmount() : null)
                            .paymentStatus(supply != null ? supply.getPaymentStatus() : null)
                            .build();
                }).toList();
        BigDecimal supplierAdvanceCredit = supplierRepository.findById(payment.getSupplierId())
                .map(Supplier::getAdvanceCredit).map(this::nvlMoney).orElse(BigDecimal.ZERO);
        return SupplierPaymentResponseDto.builder()
                .supplierPaymentId(payment.getSupplierPaymentId())
                .branchId(payment.getBranchId())
                .supplierId(payment.getSupplierId())
                .supplyId(payment.getSupplyId())
                .poId(payment.getPoId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .paymentDate(payment.getPaymentDate())
                .paidBy(payment.getPaidBy())
                .cashSessionId(payment.getCashSessionId())
                .referenceNo(payment.getReferenceNo())
                .note(payment.getNote())
                .allocatedAmount(nvlMoney(payment.getAllocatedAmount()))
                .advanceCreditAdded(nvlMoney(payment.getAdvanceCreditAdded()))
                .advanceCreditUsed(nvlMoney(payment.getAdvanceCreditUsed()))
                .supplierAdvanceCredit(supplierAdvanceCredit)
                .allocations(allocations)
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

    private BigDecimal getBatchQtyTotal(Long branchId, Long itemId, Long variantId, StockQtyType type) {
        return stockBatchRepository.findByBranchIdAndItemIdAndVariantId(branchId, itemId, variantId)
                .stream()
                .map(batch -> switch (type) {
                    case AVAILABLE -> nvlQty(batch.getAvailableQty() != null ? batch.getAvailableQty() : batch.getQtyRemaining());
                    case DAMAGED -> nvlQty(batch.getDamagedQty());
                    case EXPIRED -> nvlQty(batch.getExpiredQty());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void deductBatchQtyFifo(Long branchId, Long itemId, Long variantId, BigDecimal qtyBase, String type) {
        List<StockBatch> batches = stockBatchRepository.findByBranchIdAndItemIdAndVariantIdOrderByCreatedAtDesc(branchId, itemId, variantId);
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

    private Long resolveBatchVariantId(Long branchId, Long itemId, Long requestedVariantId, String batchBarcode) {
        if (batchBarcode == null || batchBarcode.isBlank()) {
            return requestedVariantId;
        }

        return stockBatchRepository.findByBranchIdAndInternalBatchBarcode(branchId, batchBarcode)
                .filter(batch -> itemId.equals(batch.getItemId()))
                .map(batch -> batch.getVariantId() != null ? batch.getVariantId() : requestedVariantId)
                .orElse(requestedVariantId);
    }

    private String resolveVariantSku(Long variantId) {
        if (variantId == null) {
            return null;
        }
        return itemVariantRepository.findById(variantId)
                .map(ItemVariant::getSku)
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

    private String buildPurchaseReturnCashRefundNote(PurchaseReturn purchaseReturn, Supplier supplier) {
        String note = "Purchase return cash refund"
                + " | Purchase Return #" + purchaseReturn.getPurchaseReturnId()
                + " | Supplier: " + supplier.getName()
                + " (#" + purchaseReturn.getSupplierId() + ")"
                + " | Supply #" + purchaseReturn.getSupplyId()
                + " | Reason: " + safe(purchaseReturn.getReason());
        return note;
    }

    private Long resolveCashSessionCounterId(Long cashSessionId) {
        if (cashSessionId == null) {
            return null;
        }
        return cashSessionRepository.findById(cashSessionId)
                .map(CashSession::getCounterId)
                .orElse(null);
    }

    private PurchaseReturnResponseDto mapPurchaseReturn(
        PurchaseReturn purchaseReturn,
        List<PurchaseReturnItemResponseDto> items
) {
    return PurchaseReturnResponseDto.builder()
            .purchaseReturnId(purchaseReturn.getPurchaseReturnId())
            .returnNo(purchaseReturn.getReturnNo() != null
                    ? purchaseReturn.getReturnNo() : "PR-" + purchaseReturn.getPurchaseReturnId())
            .branchId(purchaseReturn.getBranchId())
            .supplierId(purchaseReturn.getSupplierId())
            .supplyId(purchaseReturn.getSupplyId())
            .returnDate(purchaseReturn.getReturnDate())
            .refundMethod(purchaseReturn.getRefundMethod())
            .counterId(resolveCashSessionCounterId(purchaseReturn.getCashSessionId()))
            .cashSessionId(purchaseReturn.getCashSessionId())
            .bankReference(purchaseReturn.getBankReference())
            .refundAmount(purchaseReturn.getRefundAmount())
            .paidAmount(nvlMoney(purchaseReturn.getPaidAmount()))
            .balanceDue(purchaseReturn.getBalanceDue() != null
                    ? purchaseReturn.getBalanceDue()
                    : nvlMoney(purchaseReturn.getRefundAmount()).subtract(nvlMoney(purchaseReturn.getPaidAmount())))
            .paymentStatus(purchaseReturn.getPaymentStatus() != null
                    ? purchaseReturn.getPaymentStatus() : "UNPAID")
            .reason(purchaseReturn.getReason())
            .processedBy(purchaseReturn.getProcessedBy())
            .status(purchaseReturn.getStatus())
            .items(items)
            .build();
}

private PurchaseReturnRefundResponseDto mapPurchaseReturnRefund(PurchaseReturnRefund refund) {
    return PurchaseReturnRefundResponseDto.builder()
            .refundId(refund.getRefundId())
            .purchaseReturnId(refund.getPurchaseReturnId())
            .branchId(refund.getBranchId())
            .amount(refund.getAmount())
            .refundMethod(refund.getRefundMethod())
            .referenceNo(refund.getReferenceNo())
            .bankReference(refund.getBankReference())
            .counterId(refund.getCounterId())
            .cashSessionId(refund.getCashSessionId())
            .processedBy(refund.getProcessedBy())
            .processedByName(resolveUserName(refund.getProcessedBy()))
            .refundedAt(refund.getRefundedAt())
            .build();
}

private PurchaseReturnItemResponseDto mapPurchaseReturnItem(PurchaseReturnItem item) {
    ItemUnit unit = findItemUnit(item.getItemId(), item.getUnitId());
    return PurchaseReturnItemResponseDto.builder()
            .purchaseReturnItemId(item.getPurchaseReturnItemId())
            .purchaseReturnId(item.getPurchaseReturnId())
            .itemId(item.getItemId())
            .variantId(item.getVariantId())
            .variantSku(resolveVariantSku(item.getVariantId()))
            .variantLabel(resolveVariantLabel(item.getVariantId()))
            .unitId(item.getUnitId())
            .masterUnitId(unit != null ? unit.getMasterUnitId() : null)
            .unitName(resolveUnitName(unit))
            .internalBatchBarcode(item.getInternalBatchBarcode())
            .returnStockType(item.getReturnStockType())
            .quantity(item.getQuantity())
            .unitCost(item.getUnitCost())
            .lineTotal(item.getLineTotal())
            .build();
}

private ItemUnit findItemUnit(Long itemId, Long unitId) {
    if (itemId == null || unitId == null) {
        return null;
    }

    return itemUnitRepository.findById(unitId)
            .filter(unit -> itemId.equals(unit.getItemId()))
            .orElse(null);
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
}
