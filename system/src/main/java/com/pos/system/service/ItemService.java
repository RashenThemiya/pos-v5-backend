package com.pos.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.system.dto.item.ItemSearchRequest;
import com.pos.system.dto.item.ItemRequest;
import com.pos.system.dto.item.ItemResponse;
import com.pos.system.dto.item.ItemUnitRequest;
import com.pos.system.dto.item.ItemUnitResponse;
import com.pos.system.dto.item.ItemVariantAttributeRequest;
import com.pos.system.dto.item.ItemVariantAttributeResponse;
import com.pos.system.dto.item.ItemVariantRequest;
import com.pos.system.dto.item.ItemVariantResponse;
import com.pos.system.model.catalog.Brand;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.ItemVariant;
import com.pos.system.model.catalog.ItemVariantAttribute;
import com.pos.system.model.catalog.ScaleItemMapping;
import com.pos.system.model.catalog.UnitMaster;
import com.pos.system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final ItemVariantAttributeRepository itemVariantAttributeRepository;
    private final UnitMasterRepository unitMasterRepository;
    private final ScaleItemMappingRepository scaleItemMappingRepository;
    private final StockRepository stockRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockTransferItemRepository stockTransferItemRepository;
    private final StockCountItemRepository stockCountItemRepository;
    private final OrderProductRepository orderProductRepository;
    private final SalesReturnItemRepository salesReturnItemRepository;
    private final SupplierItemRepository supplierItemRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplyProductRepository supplyProductRepository;
    private final PurchaseReturnItemRepository purchaseReturnItemRepository;

    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final BrandCategoryRepository brandCategoryRepository;

    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ItemResponse create(ItemRequest request) {
        validateBranch(request.getBranchId());
        validateItemRequest(request, null);

        Item item = new Item();
        mapRequestToItem(request, item);

        if (request.getImageFile() != null && !request.getImageFile().isEmpty()) {
            item.setImage(fileStorageService.uploadItemImage(request.getImageFile()));
        }

        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        item = itemRepository.save(item);

        ItemUnit baseUnit = createOrUpdateBaseUnit(item, buildBaseUnitRequest(request), request.getAutoGenerateBarcode());
        syncScaleMapping(item, baseUnit, request.getScaleItemCode());
        List<ItemVariantRequest> variantRequests = resolveVariantRequests(request);
        if (variantRequests != null) {
            syncVariants(item, variantRequests);
        }

        return toResponse(item);
    }

    public List<ItemResponse> getAllByBranch(Long branchId) {
        validateBranch(branchId);
        return itemRepository.findByBranchId(branchId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ItemResponse> getActiveByBranch(Long branchId) {
        validateBranch(branchId);
        return itemRepository.findByBranchIdAndIsActive(branchId, true)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ItemResponse> getByCategory(Long branchId, Long categoryId) {
        validateBranch(branchId);
        return itemRepository.findByBranchIdAndCategoryId(branchId, categoryId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ItemResponse> getByBrand(Long branchId, Long brandId) {
        validateBranch(branchId);
        return itemRepository.findByBranchIdAndBrandId(branchId, brandId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Page<ItemResponse> search(ItemSearchRequest request, Pageable pageable) {
        if (request.getBranchId() != null) {
            validateBranch(request.getBranchId());
        }

        Specification<Item> specification = (root, query, cb) -> cb.conjunction();

        if (request.getBranchId() != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("branchId"), request.getBranchId()));
        }

        if (StringUtils.hasText(request.getQ())) {
            specification = specification.and(buildSearchSpecification(request.getBranchId(), request.getQ()));
        }

        return itemRepository.findAll(specification, pageable).map(this::toResponse);
    }

    public ItemResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public ItemResponse getBySku(Long branchId, String sku) {
        validateBranch(branchId);
        Item item = itemRepository.findByBranchIdAndSku(branchId, sku)
                .orElseThrow(() -> new RuntimeException("Item not found with SKU: " + sku));
        return toResponse(item);
    }

    public ItemResponse getByBarcode(Long branchId, String barcode) {
        validateBranch(branchId);
        String normalized = normalizeBarcode(barcode);

        ItemUnit unit = itemUnitRepository.findByBranchIdAndBarcode(branchId, normalized)
                .orElseThrow(() -> new RuntimeException("Item not found with barcode: " + barcode));

        return toResponse(findById(unit.getItemId()));
    }

    @Transactional
    public ItemResponse update(Long id, ItemRequest request) {
        Item item = findById(id);

        if (!item.getBranchId().equals(request.getBranchId())) {
            throw new RuntimeException("Branch cannot be changed for an existing item");
        }

        validateItemRequest(request, item);

        if (!item.getSku().equals(request.getSku()) &&
                itemRepository.existsByBranchIdAndSku(item.getBranchId(), request.getSku())) {
            throw new RuntimeException("SKU already exists in this branch: " + request.getSku());
        }

        mapRequestToItem(request, item);

        if (request.getImageFile() != null && !request.getImageFile().isEmpty()) {
            item.setImage(fileStorageService.uploadItemImage(request.getImageFile()));
        }

        item.setUpdatedAt(LocalDateTime.now());
        item = itemRepository.save(item);

        ItemUnit baseUnit = createOrUpdateBaseUnit(item, buildBaseUnitRequest(request), request.getAutoGenerateBarcode());
        syncScaleMapping(item, baseUnit, request.getScaleItemCode());
        List<ItemVariantRequest> variantRequests = resolveVariantRequests(request);
        if (variantRequests != null) {
            syncVariants(item, variantRequests);
        }

        return toResponse(item);
    }

    @Transactional
    public ItemResponse toggleActive(Long id) {
        Item item = findById(id);
        item.setIsActive(!item.getIsActive());
        item.setUpdatedAt(LocalDateTime.now());
        return toResponse(itemRepository.save(item));
    }

    @Transactional
    public void delete(Long id) {
        Item item = findById(id);

        deleteVariantsByItem(item.getItemId());

        List<ItemUnit> units = itemUnitRepository.findByItemId(item.getItemId());
        itemUnitRepository.deleteAll(units);

        scaleItemMappingRepository.findByItemIdAndIsActiveTrue(item.getItemId())
                .ifPresent(scaleItemMappingRepository::delete);

        itemRepository.delete(item);
    }

    public List<ItemUnitResponse> getUnitsByItem(Long itemId) {
        findById(itemId);
        return itemUnitRepository.findByItemId(itemId)
                .stream()
                .map(this::toUnitResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ItemUnitResponse addUnit(Long itemId, ItemUnitRequest request, Boolean autoGenerateBarcode) {
        Item item = findById(itemId);
        validateAdditionalUnitRequest(item, request, null);

        ItemUnit unit = new ItemUnit();
        unit.setBranchId(item.getBranchId());
        unit.setItemId(item.getItemId());
        UnitMaster masterUnit = request.getMasterUnitId() != null
                ? resolveMasterUnitForAssignment(item, request.getMasterUnitId(), null, true)
                : resolveOrCreateMasterUnitForAssignment(item, request.getUnitName(), null);
        unit.setMasterUnitId(masterUnit.getUnitId());
        unit.setUnitName(masterUnit.getName());
        unit.setMultiplierToBase(request.getMultiplierToBase());
        unit.setDefaultSellingPrice(defaultMoney(request.getDefaultSellingPrice()));
        unit.setIsBaseUnit(false);
        unit.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        String incomingBarcode = normalizeBarcode(request.getBarcode());

        if (incomingBarcode == null && !Boolean.TRUE.equals(item.getIsWeighed()) && Boolean.TRUE.equals(autoGenerateBarcode)) {
            incomingBarcode = generateUniqueInternalBarcode(item.getBranchId());
        }

        if (incomingBarcode != null && itemUnitRepository.existsByBranchIdAndBarcode(item.getBranchId(), incomingBarcode)) {
            throw new RuntimeException("Barcode already exists in this branch: " + incomingBarcode);
        }

        unit.setBarcode(Boolean.TRUE.equals(item.getIsWeighed()) ? null : incomingBarcode);
        unit.setCreatedAt(LocalDateTime.now());
        unit.setUpdatedAt(LocalDateTime.now());

        return toUnitResponse(itemUnitRepository.save(unit));
    }

    @Transactional
    public ItemUnitResponse updateUnit(Long itemId, Long unitId, ItemUnitRequest request, Boolean autoGenerateBarcode) {
        Item item = findById(itemId);

        ItemUnit unit = itemUnitRepository.findById(unitId)
                .orElseThrow(() -> new RuntimeException("Item unit not found with id: " + unitId));

        if (!unit.getItemId().equals(item.getItemId())) {
            throw new RuntimeException("Unit does not belong to this item");
        }

        if (Boolean.TRUE.equals(unit.getIsBaseUnit())) {
            throw new RuntimeException("Base unit must be updated through item update");
        }

        validateAdditionalUnitRequest(item, request, unitId);

        UnitMaster masterUnit = request.getMasterUnitId() != null
                ? resolveMasterUnitForAssignment(item, request.getMasterUnitId(), unit, false)
                : resolveOrCreateMasterUnitForAssignment(item, request.getUnitName(), unit);
        unit.setMasterUnitId(masterUnit.getUnitId());
        unit.setUnitName(masterUnit.getName());
        unit.setMultiplierToBase(request.getMultiplierToBase());
        unit.setDefaultSellingPrice(defaultMoney(request.getDefaultSellingPrice()));
        unit.setIsActive(request.getIsActive() != null ? request.getIsActive() : unit.getIsActive());

        String incomingBarcode = normalizeBarcode(request.getBarcode());

        if (incomingBarcode == null && !Boolean.TRUE.equals(item.getIsWeighed()) && Boolean.TRUE.equals(autoGenerateBarcode)) {
            incomingBarcode = generateUniqueInternalBarcode(item.getBranchId());
        }

        if (incomingBarcode != null) {
            boolean duplicate = itemUnitRepository.existsByBranchIdAndBarcode(item.getBranchId(), incomingBarcode);
            if (incomingBarcode.equals(unit.getBarcode())) {
                duplicate = false;
            }
            if (duplicate) {
                throw new RuntimeException("Barcode already exists in this branch: " + incomingBarcode);
            }
        }

        unit.setBarcode(Boolean.TRUE.equals(item.getIsWeighed()) ? null : incomingBarcode);
        unit.setUpdatedAt(LocalDateTime.now());

        return toUnitResponse(itemUnitRepository.save(unit));
    }

    @Transactional
    public void deleteUnit(Long itemId, Long unitId) {
        findById(itemId);

        ItemUnit unit = itemUnitRepository.findById(unitId)
                .orElseThrow(() -> new RuntimeException("Item unit not found with id: " + unitId));

        if (!unit.getItemId().equals(itemId)) {
            throw new RuntimeException("Unit does not belong to this item");
        }

        if (Boolean.TRUE.equals(unit.getIsBaseUnit())) {
            throw new RuntimeException("Base unit cannot be deleted");
        }

        itemUnitRepository.delete(unit);
    }

    public List<ItemVariantResponse> getVariantsByItem(Long itemId) {
        findById(itemId);
        return itemVariantRepository.findByItemIdOrderByVariantIdAsc(itemId)
                .stream()
                .map(this::toVariantResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ItemVariantResponse addVariant(Long itemId, ItemVariantRequest request) {
        Item item = findById(itemId);
        request.setVariantId(null);
        validateVariantBatch(item, List.of(request), null);
        return toVariantResponse(saveVariant(item, new ItemVariant(), request));
    }

    @Transactional
    public ItemVariantResponse updateVariant(Long itemId, Long variantId, ItemVariantRequest request) {
        Item item = findById(itemId);
        ItemVariant variant = itemVariantRepository.findByVariantIdAndItemId(variantId, itemId)
                .orElseThrow(() -> new RuntimeException("Item variant not found with id: " + variantId));

        request.setVariantId(variantId);
        validateVariantBatch(item, List.of(request), variantId);
        return toVariantResponse(saveVariant(item, variant, request));
    }

    @Transactional
    public void deleteVariant(Long itemId, Long variantId) {
        findById(itemId);
        ItemVariant variant = itemVariantRepository.findByVariantIdAndItemId(variantId, itemId)
                .orElseThrow(() -> new RuntimeException("Item variant not found with id: " + variantId));

        validateVariantCanBeDeleted(variant);
        itemVariantAttributeRepository.deleteByVariantId(variant.getVariantId());
        itemVariantRepository.delete(variant);
    }

    @Transactional
    public ItemVariantResponse toggleVariantActive(Long itemId, Long variantId) {
        findById(itemId);
        ItemVariant variant = itemVariantRepository.findByVariantIdAndItemId(variantId, itemId)
                .orElseThrow(() -> new RuntimeException("Item variant not found with id: " + variantId));

        variant.setIsActive(!variant.getIsActive());
        variant.setUpdatedAt(LocalDateTime.now());
        return toVariantResponse(itemVariantRepository.save(variant));
    }

    private void validateBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
    }

    private Item findById(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Item not found with id: " + id));
    }

    private void validateItemRequest(ItemRequest request, Item existingItem) {
        if (request.getBranchId() == null) {
            throw new RuntimeException("Branch ID is required");
        }

        validateBranch(request.getBranchId());

        if (request.getSku() == null || request.getSku().trim().isEmpty()) {
            throw new RuntimeException("SKU is required");
        }

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new RuntimeException("Item name is required");
        }

        if (existingItem == null && itemRepository.existsByBranchIdAndSku(request.getBranchId(), request.getSku())) {
            throw new RuntimeException("SKU already exists in this branch: " + request.getSku());
        }

        if (request.getMinStock() != null &&
                request.getMinStock().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Minimum stock must be >= 0");
        }

        if (request.getMaxStock() != null &&
                request.getMaxStock().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Maximum stock must be >= 0");
        }

        if (request.getMinStock() != null &&
                request.getMaxStock() != null &&
                request.getMinStock().compareTo(request.getMaxStock()) > 0) {
            throw new RuntimeException("Minimum stock cannot be greater than maximum stock");
        }

        if (request.getCategoryId() != null) {
            categoryRepository.findByCategoryIdAndBranchId(request.getCategoryId(), request.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Category not found in this branch"));
        }

        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findByBrandIdAndBranchId(request.getBrandId(), request.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Brand not found in this branch"));
            validateBrandCategory(request, existingItem, brand);
        }

        ItemUnitRequest unit = buildBaseUnitRequest(request);

        if (unit.getMasterUnitId() == null && (unit.getUnitName() == null || unit.getUnitName().trim().isEmpty())) {
            throw new RuntimeException("Base unit is required");
        }

        if (unit.getMultiplierToBase() == null || unit.getMultiplierToBase().compareTo(BigDecimal.ONE) != 0) {
            throw new RuntimeException("Base unit multiplierToBase must be 1");
        }

        if (unit.getDefaultSellingPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Default selling price must be >= 0");
        }

        if (Boolean.TRUE.equals(request.getIsWeighed())) {
            if (request.getScaleItemCode() == null || request.getScaleItemCode().trim().isEmpty()) {
                throw new RuntimeException("Scale item code is required for weighed item");
            }
        }

        List<ItemVariantRequest> variantRequests = resolveVariantRequests(request);
        if (Boolean.TRUE.equals(request.getIsWeighed()) && variantRequests != null && !variantRequests.isEmpty()) {
            throw new RuntimeException("Variants are not supported for weighed items");
        }
    }

    private void validateAdditionalUnitRequest(Item item, ItemUnitRequest request, Long currentUnitId) {
        if (request == null) {
            throw new RuntimeException("Unit request is required");
        }

        if (request.getMasterUnitId() == null && (request.getUnitName() == null || request.getUnitName().trim().isEmpty())) {
            throw new RuntimeException("Unit is required");
        }

        if (request.getMultiplierToBase() == null || request.getMultiplierToBase().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Multiplier to base must be greater than 0");
        }

        if (request.getDefaultSellingPrice() != null && request.getDefaultSellingPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Default selling price must be >= 0");
        }
    }

    private void mapRequestToItem(ItemRequest request, Item item) {
        item.setBranchId(request.getBranchId());
        item.setSku(request.getSku().trim());
        item.setName(request.getName().trim());
        item.setCategoryId(request.getCategoryId());
        item.setBrandId(request.getBrandId());
        item.setIsWeighed(request.getIsWeighed() != null ? request.getIsWeighed() : false);
        item.setScaleBarcodePrefix(request.getScaleBarcodePrefix());
        item.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        item.setMinStock(request.getMinStock());
        item.setMaxStock(request.getMaxStock());
    }

    private void validateBrandCategory(ItemRequest request, Item existingItem, Brand brand) {
        if (request.getCategoryId() == null) {
            return;
        }

        boolean categoryChanged = existingItem == null
                || !request.getCategoryId().equals(existingItem.getCategoryId());
        boolean brandChanged = existingItem == null
                || !request.getBrandId().equals(existingItem.getBrandId());

        if (!categoryChanged && !brandChanged) {
            return;
        }

        if (!brandCategoryRepository.existsByBrandIdAndCategoryId(brand.getBrandId(), request.getCategoryId())) {
            throw new RuntimeException("Brand is not mapped to the selected category");
        }
    }

    private ItemUnitRequest buildBaseUnitRequest(ItemRequest request) {
        ItemUnitRequest unit = new ItemUnitRequest();
        unit.setMasterUnitId(request.getBaseMasterUnitId());
        unit.setUnitName(request.getBaseUnitName());
        unit.setMultiplierToBase(new BigDecimal(request.getBaseUnitMultiplierToBase()));
        unit.setBarcode(request.getBaseUnitBarcode());
        unit.setDefaultSellingPrice(parseOptionalMoney(request.getBaseUnitDefaultSellingPrice()));
        unit.setIsActive(request.getBaseUnitIsActive());
        return unit;
    }

    private ItemUnit createOrUpdateBaseUnit(Item item, ItemUnitRequest request, Boolean autoGenerateBarcode) {
        ItemUnit unit = itemUnitRepository.findByItemIdAndIsBaseUnitTrue(item.getItemId())
                .orElse(new ItemUnit());

        unit.setBranchId(item.getBranchId());
        unit.setItemId(item.getItemId());
        UnitMaster masterUnit = request.getMasterUnitId() != null
                ? resolveMasterUnitForAssignment(item, request.getMasterUnitId(), unit, unit.getUnitId() == null)
                : resolveOrCreateMasterUnit(item.getBranchId(), request.getUnitName());
        unit.setMasterUnitId(masterUnit.getUnitId());
        unit.setUnitName(masterUnit.getName());
        unit.setMultiplierToBase(BigDecimal.ONE);
        unit.setDefaultSellingPrice(request.getDefaultSellingPrice() != null ? request.getDefaultSellingPrice() : BigDecimal.ZERO);
        unit.setIsBaseUnit(true);
        unit.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        String incomingBarcode = normalizeBarcode(request.getBarcode());

        if (incomingBarcode == null && !Boolean.TRUE.equals(item.getIsWeighed()) && Boolean.TRUE.equals(autoGenerateBarcode)) {
            incomingBarcode = generateUniqueInternalBarcode(item.getBranchId());
        }

        if (incomingBarcode != null) {
            boolean duplicate = itemUnitRepository.existsByBranchIdAndBarcode(item.getBranchId(), incomingBarcode);
            if (unit.getUnitId() != null && incomingBarcode.equals(unit.getBarcode())) {
                duplicate = false;
            }
            if (duplicate) {
                throw new RuntimeException("Barcode already exists in this branch: " + incomingBarcode);
            }
        }

        unit.setBarcode(Boolean.TRUE.equals(item.getIsWeighed()) ? null : incomingBarcode);

        if (unit.getUnitId() == null) {
            unit.setCreatedAt(LocalDateTime.now());
        }
        unit.setUpdatedAt(LocalDateTime.now());

        return itemUnitRepository.save(unit);
    }

    private BigDecimal parseOptionalMoney(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private void syncScaleMapping(Item item, ItemUnit baseUnit, String scaleItemCode) {
        ScaleItemMapping existing = scaleItemMappingRepository.findByItemIdAndIsActiveTrue(item.getItemId())
                .orElse(null);

        if (!Boolean.TRUE.equals(item.getIsWeighed())) {
            if (existing != null) {
                scaleItemMappingRepository.delete(existing);
            }
            return;
        }

        String code = scaleItemCode.trim();

        ScaleItemMapping byCode = scaleItemMappingRepository
                .findByBranchIdAndScaleItemCodeAndIsActiveTrue(item.getBranchId(), code)
                .orElse(null);

        if (byCode != null && !byCode.getItemId().equals(item.getItemId())) {
            throw new RuntimeException("Scale item code already mapped in this branch: " + code);
        }

        if (existing == null) {
            existing = new ScaleItemMapping();
            existing.setBranchId(item.getBranchId());
            existing.setCreatedAt(LocalDateTime.now());
            existing.setIsActive(true);
        }

        existing.setScaleItemCode(code);
        existing.setItemId(item.getItemId());
        existing.setUnitId(baseUnit.getUnitId());

        scaleItemMappingRepository.save(existing);
    }

    private String normalizeBarcode(String barcode) {
        if (barcode == null) {
            return null;
        }
        String cleaned = barcode.trim().replace(" ", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String generateUniqueInternalBarcode(Long branchId) {
        String barcode;
        do {
            long randomPart = ThreadLocalRandom.current().nextLong(100000000000L, 999999999999L);
            barcode = "2" + String.valueOf(randomPart).substring(0, 12);
        } while (itemUnitRepository.existsByBranchIdAndBarcode(branchId, barcode));
        return barcode;
    }

    private ItemResponse toResponse(Item item) {
        ItemResponse response = new ItemResponse();
        response.setItemId(item.getItemId());
        response.setBranchId(item.getBranchId());
        response.setSku(item.getSku());
        response.setName(item.getName());
        response.setImage(item.getImage());
        response.setCategoryId(item.getCategoryId());
        response.setBrandId(item.getBrandId());
        response.setIsWeighed(item.getIsWeighed());
        response.setScaleBarcodePrefix(item.getScaleBarcodePrefix());
        response.setIsActive(item.getIsActive());
        response.setMinStock(item.getMinStock());
        response.setMaxStock(item.getMaxStock());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());

        if (item.getCategoryId() != null) {
            categoryRepository.findById(item.getCategoryId())
                    .ifPresent(cat -> response.setCategoryName(cat.getName()));
        }

        if (item.getBrandId() != null) {
            brandRepository.findById(item.getBrandId())
                    .ifPresent(brand -> response.setBrandName(brand.getName()));
        }

        scaleItemMappingRepository.findByItemIdAndIsActiveTrue(item.getItemId())
                .ifPresent(mapping -> response.setScaleItemCode(mapping.getScaleItemCode()));

        List<ItemUnitResponse> units = itemUnitRepository.findByItemId(item.getItemId())
                .stream()
                .map(this::toUnitResponse)
                .collect(Collectors.toList());

        response.setUnits(units);
        response.setVariants(getVariantsByItem(item.getItemId()));
        return response;
    }

    private Specification<Item> buildSearchSpecification(Long branchId, String queryText) {
        String trimmed = queryText.trim();
        String like = "%" + trimmed.toLowerCase() + "%";

        return (root, query, cb) -> {
            Set<Long> matchingItemIds = new HashSet<>(itemUnitRepository.findDistinctItemIdsByBranchIdAndBarcodeLike(branchId, trimmed));

            if (branchId != null) {
                matchingItemIds.addAll(findMatchingItemIdsByText(branchId, trimmed));
            }

            Specification<Item> textSpecification = (textRoot, textQuery, textCb) -> textCb.or(
                    textCb.like(textCb.lower(textRoot.get("sku")), like),
                    textCb.like(textCb.lower(textRoot.get("name")), like),
                    textCb.like(textCb.lower(textRoot.get("scaleBarcodePrefix")), like)
            );

            if (isNumeric(trimmed)) {
                Long itemId = Long.valueOf(trimmed);
                textSpecification = textSpecification.or((textRoot, textQuery, textCb) -> textCb.equal(textRoot.get("itemId"), itemId));
            }

            if (!matchingItemIds.isEmpty()) {
                Specification<Item> barcodeSpecification = (textRoot, textQuery, textCb) -> textRoot.get("itemId").in(matchingItemIds);
                textSpecification = textSpecification.or(barcodeSpecification);
            }

            return textSpecification.toPredicate(root, query, cb);
        };
    }

    private List<Long> findMatchingItemIdsByText(Long branchId, String queryText) {
        String like = queryText.trim().toLowerCase();

        return itemRepository.findAll((root, query, cb) -> cb.or(
                        cb.like(cb.lower(root.get("sku")), "%" + like + "%"),
                        cb.like(cb.lower(root.get("name")), "%" + like + "%"),
                        cb.like(cb.lower(root.get("scaleBarcodePrefix")), "%" + like + "%")
                ))
                .stream()
                .filter(item -> branchId == null || branchId.equals(item.getBranchId()))
                .map(Item::getItemId)
                .collect(Collectors.toList());
    }

    private boolean isNumeric(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private ItemUnitResponse toUnitResponse(ItemUnit unit) {
        ItemUnitResponse response = new ItemUnitResponse();
        response.setUnitId(unit.getUnitId());
        response.setItemId(unit.getItemId());
        response.setMasterUnitId(unit.getMasterUnitId());
        response.setUnitName(resolveUnitName(unit));
        response.setMultiplierToBase(unit.getMultiplierToBase());
        response.setBarcode(unit.getBarcode());
        response.setDefaultSellingPrice(unit.getDefaultSellingPrice());
        response.setIsBaseUnit(unit.getIsBaseUnit());
        response.setIsActive(unit.getIsActive());
        response.setCreatedAt(unit.getCreatedAt());
        response.setUpdatedAt(unit.getUpdatedAt());
        return response;
    }

    private UnitMaster resolveMasterUnitForAssignment(Item item, Long masterUnitId, ItemUnit currentUnit, boolean creating) {
        if (masterUnitId == null) {
            throw new RuntimeException("Unit is required");
        }

        UnitMaster masterUnit = unitMasterRepository.findByUnitIdAndBranchId(masterUnitId, item.getBranchId())
                .orElseThrow(() -> new RuntimeException("Unit not found in this branch"));

        boolean sameUnit = currentUnit != null && masterUnitId.equals(currentUnit.getMasterUnitId());
        if (!Boolean.TRUE.equals(masterUnit.getIsActive()) && (creating || !sameUnit)) {
            throw new RuntimeException("Inactive units cannot be assigned to items");
        }

        boolean duplicate = currentUnit == null
                ? itemUnitRepository.existsByItemIdAndMasterUnitId(item.getItemId(), masterUnitId)
                : itemUnitRepository.existsByItemIdAndMasterUnitIdAndUnitIdNot(item.getItemId(), masterUnitId, currentUnit.getUnitId());

        if (duplicate) {
            throw new RuntimeException("Unit already assigned to this item: " + masterUnit.getName());
        }

        return masterUnit;
    }

    private UnitMaster resolveOrCreateMasterUnit(Long branchId, String unitName) {
        if (unitName == null || unitName.trim().isEmpty()) {
            throw new RuntimeException("Base unit is required");
        }

        String normalized = unitName.trim().toUpperCase();
        return unitMasterRepository.findByBranchId(branchId)
                .stream()
                .filter(unit -> normalized.equals(unit.getName()))
                .findFirst()
                .orElseGet(() -> {
                    UnitMaster unit = new UnitMaster();
                    unit.setBranchId(branchId);
                    unit.setName(normalized);
                    unit.setIsActive(true);
                    unit.setCreatedAt(LocalDateTime.now());
                    unit.setUpdatedAt(LocalDateTime.now());
                    return unitMasterRepository.save(unit);
                });
    }

    private UnitMaster resolveOrCreateMasterUnitForAssignment(Item item, String unitName, ItemUnit currentUnit) {
        UnitMaster masterUnit = resolveOrCreateMasterUnit(item.getBranchId(), unitName);
        boolean duplicate = currentUnit == null
                ? itemUnitRepository.existsByItemIdAndMasterUnitId(item.getItemId(), masterUnit.getUnitId())
                : itemUnitRepository.existsByItemIdAndMasterUnitIdAndUnitIdNot(item.getItemId(), masterUnit.getUnitId(), currentUnit.getUnitId());

        if (duplicate) {
            throw new RuntimeException("Unit already assigned to this item: " + masterUnit.getName());
        }

        return masterUnit;
    }

    private String resolveUnitName(ItemUnit itemUnit) {
        if (itemUnit.getMasterUnitId() != null) {
            return unitMasterRepository.findById(itemUnit.getMasterUnitId())
                    .map(UnitMaster::getName)
                    .orElse(itemUnit.getUnitName());
        }

        return itemUnit.getUnitName();
    }

    private List<ItemVariantRequest> resolveVariantRequests(ItemRequest request) {
        if (request.getVariants() != null) {
            return request.getVariants();
        }

        if (!StringUtils.hasText(request.getVariantsJson())) {
            return null;
        }

        try {
            return objectMapper.readValue(request.getVariantsJson(), new TypeReference<List<ItemVariantRequest>>() {});
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Invalid variants payload");
        }
    }

    private void syncVariants(Item item, List<ItemVariantRequest> requests) {
        validateVariantBatch(item, requests, null);

        List<ItemVariant> existingVariants = itemVariantRepository.findByItemIdOrderByVariantIdAsc(item.getItemId());
        Set<Long> requestedIds = requests.stream()
                .map(ItemVariantRequest::getVariantId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        List<Long> variantIdsToDelete = existingVariants.stream()
                .map(ItemVariant::getVariantId)
                .filter(id -> !requestedIds.contains(id))
                .collect(Collectors.toList());

        for (ItemVariant existingVariant : existingVariants) {
            if (variantIdsToDelete.contains(existingVariant.getVariantId())) {
                validateVariantCanBeDeleted(existingVariant);
            }
        }

        if (!variantIdsToDelete.isEmpty()) {
            itemVariantAttributeRepository.deleteByVariantIdIn(variantIdsToDelete);
            itemVariantRepository.deleteAllById(variantIdsToDelete);
        }

        Map<Long, ItemVariant> existingById = existingVariants.stream()
                .collect(Collectors.toMap(ItemVariant::getVariantId, variant -> variant));

        for (ItemVariantRequest request : requests) {
            ItemVariant variant;
            if (request.getVariantId() != null) {
                variant = existingById.get(request.getVariantId());
                if (variant == null) {
                    throw new RuntimeException("Item variant not found with id: " + request.getVariantId());
                }
            } else {
                variant = new ItemVariant();
            }
            saveVariant(item, variant, request);
        }
    }

    private void validateVariantBatch(Item item, List<ItemVariantRequest> requests, Long currentVariantId) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        if (Boolean.TRUE.equals(item.getIsWeighed())) {
            throw new RuntimeException("Variants are not supported for weighed items");
        }

        if (requests.size() > 500) {
            throw new RuntimeException("Too many variants in one request");
        }

        Set<String> combinations = new HashSet<>();
        Set<Long> requestIds = new HashSet<>();
        Set<String> requestSkus = new HashSet<>();

        for (ItemVariantRequest request : requests) {
            if (request == null) {
                throw new RuntimeException("Variant request is required");
            }

            if (request.getVariantId() != null && !requestIds.add(request.getVariantId())) {
                throw new RuntimeException("Duplicate variant id in request: " + request.getVariantId());
            }

            if (request.getDefaultSellingPrice() != null && request.getDefaultSellingPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Variant selling price must be >= 0");
            }

            String sku = normalizeVariantSku(request.getSku());
            if (sku != null) {
                String normalizedSku = sku.toLowerCase(Locale.ROOT);
                if (!requestSkus.add(normalizedSku)) {
                    throw new RuntimeException("Duplicate variant SKU in request: " + sku);
                }

                if (sku.equalsIgnoreCase(item.getSku())) {
                    throw new RuntimeException("Variant SKU cannot duplicate the item SKU: " + sku);
                }

                boolean duplicateItemSku = itemRepository.existsByBranchIdAndSku(item.getBranchId(), sku);
                if (duplicateItemSku) {
                    throw new RuntimeException("Variant SKU already exists as an item SKU in this branch: " + sku);
                }

                boolean duplicateVariantSku = request.getVariantId() == null
                        ? itemVariantRepository.existsByBranchIdAndSku(item.getBranchId(), sku)
                        : itemVariantRepository.existsByBranchIdAndSkuAndVariantIdNot(item.getBranchId(), sku, request.getVariantId());
                duplicateVariantSku = duplicateVariantSku || (request.getVariantId() == null
                        ? itemVariantRepository.existsByBranchIdAndVariantSku(item.getBranchId(), sku)
                        : itemVariantRepository.existsByBranchIdAndVariantSkuAndVariantIdNot(item.getBranchId(), sku, request.getVariantId()));
                if (currentVariantId != null && currentVariantId.equals(request.getVariantId())) {
                    duplicateVariantSku = itemVariantRepository.existsByBranchIdAndSkuAndVariantIdNot(item.getBranchId(), sku, currentVariantId)
                            || itemVariantRepository.existsByBranchIdAndVariantSkuAndVariantIdNot(item.getBranchId(), sku, currentVariantId);
                }
                if (duplicateVariantSku) {
                    throw new RuntimeException("Variant SKU already exists in this branch: " + sku);
                }
            }

            String combination = buildVariantCombinationKey(request.getAttributes());
            if (!combinations.add(combination)) {
                throw new RuntimeException("Duplicate variant attribute combination: " + describeAttributes(request.getAttributes()));
            }
        }

        if (requests.size() == 1 || currentVariantId != null) {
            Set<String> existingCombinations = itemVariantRepository.findByItemIdOrderByVariantIdAsc(item.getItemId())
                    .stream()
                    .filter(variant -> currentVariantId == null || !currentVariantId.equals(variant.getVariantId()))
                    .map(this::buildExistingVariantCombinationKey)
                    .collect(Collectors.toSet());

            for (String combination : combinations) {
                if (existingCombinations.contains(combination)) {
                    throw new RuntimeException("Duplicate variant attribute combination");
                }
            }
        }
    }

    private ItemVariant saveVariant(Item item, ItemVariant variant, ItemVariantRequest request) {
        boolean creating = variant.getVariantId() == null;
        String normalizedSku = normalizeVariantSku(request.getSku());
        String combinationSignature = buildVariantCombinationKey(request.getAttributes());
        variant.setItemId(item.getItemId());
        variant.setBranchId(item.getBranchId());
        variant.setSku(normalizedSku);
        variant.setVariantSku(normalizedSku != null ? normalizedSku : buildInternalVariantSku(item, combinationSignature));
        variant.setDefaultSellingPrice(request.getDefaultSellingPrice());
        variant.setCombinationSignature(combinationSignature);
        variant.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        if (creating) {
            variant.setCreatedAt(LocalDateTime.now());
        }
        variant.setUpdatedAt(LocalDateTime.now());

        ItemVariant saved = itemVariantRepository.save(variant);
        itemVariantAttributeRepository.deleteByVariantId(saved.getVariantId());
        itemVariantAttributeRepository.flush();
        itemVariantAttributeRepository.saveAll(toVariantAttributes(saved.getVariantId(), request.getAttributes()));
        return saved;
    }

    private String buildInternalVariantSku(Item item, String combinationSignature) {
        String seed = item.getBranchId() + ":" + item.getItemId() + ":" + combinationSignature;
        return "V" + item.getItemId() + "-" + java.util.UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }

    private List<ItemVariantAttribute> toVariantAttributes(Long variantId, List<ItemVariantAttributeRequest> attributes) {
        return attributes.stream()
                .sorted(Comparator.comparing(attribute -> normalizeAttributeToken(attribute.getAttributeName())))
                .map(attribute -> {
                    ItemVariantAttribute entity = new ItemVariantAttribute();
                    entity.setVariantId(variantId);
                    entity.setAttributeName(attribute.getAttributeName().trim());
                    entity.setAttributeValue(attribute.getAttributeValue().trim());
                    return entity;
                })
                .collect(Collectors.toList());
    }

    private String buildVariantCombinationKey(List<ItemVariantAttributeRequest> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            throw new RuntimeException("Variant must have at least one attribute");
        }

        Set<String> names = new HashSet<>();
        List<String> parts = new ArrayList<>();
        for (ItemVariantAttributeRequest attribute : attributes) {
            if (attribute == null) {
                throw new RuntimeException("Variant attribute is required");
            }
            if (!StringUtils.hasText(attribute.getAttributeName())) {
                throw new RuntimeException("Variant attribute name is required");
            }
            if (!StringUtils.hasText(attribute.getAttributeValue())) {
                throw new RuntimeException("Variant attribute value is required");
            }

            String name = normalizeAttributeToken(attribute.getAttributeName());
            if (!names.add(name)) {
                throw new RuntimeException("Duplicate attribute name in variant: " + attribute.getAttributeName().trim());
            }
            parts.add(name + "=" + normalizeAttributeToken(attribute.getAttributeValue()));
        }

        return parts.stream().sorted().collect(Collectors.joining("|"));
    }

    private String buildExistingVariantCombinationKey(ItemVariant variant) {
        return itemVariantAttributeRepository.findByVariantIdOrderByAttributeNameAsc(variant.getVariantId())
                .stream()
                .map(attribute -> normalizeAttributeToken(attribute.getAttributeName()) + "=" + normalizeAttributeToken(attribute.getAttributeValue()))
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private String describeAttributes(List<ItemVariantAttributeRequest> attributes) {
        if (attributes == null) {
            return "";
        }
        return attributes.stream()
                .map(attribute -> attribute.getAttributeName().trim() + "=" + attribute.getAttributeValue().trim())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private String normalizeAttributeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeVariantSku(String sku) {
        if (sku == null) {
            return null;
        }
        String trimmed = sku.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateVariantCanBeDeleted(ItemVariant variant) {
        if (variant == null) {
            throw new RuntimeException("Variant is required");
        }

        Long variantId = variant.getVariantId();
        if (stockRepository.existsByVariantId(variantId)
                || stockBatchRepository.existsByVariantId(variantId)
                || stockMovementRepository.existsByVariantId(variantId)
                || stockTransferItemRepository.existsByVariantId(variantId)
                || stockCountItemRepository.existsByVariantId(variantId)
                || orderProductRepository.existsByVariantId(variantId)
                || salesReturnItemRepository.existsByVariantId(variantId)
                || supplierItemRepository.existsByVariantId(variantId)
                || purchaseOrderItemRepository.existsByVariantId(variantId)
                || supplyProductRepository.existsByVariantId(variantId)
                || purchaseReturnItemRepository.existsByVariantId(variantId)) {
            throw new RuntimeException("Variant is already used in transactions or stock records and cannot be deleted");
        }
    }

    private void deleteVariantsByItem(Long itemId) {
        List<ItemVariant> variants = itemVariantRepository.findByItemIdOrderByVariantIdAsc(itemId);
        if (variants.isEmpty()) {
            return;
        }

        variants.forEach(this::validateVariantCanBeDeleted);
        List<Long> variantIds = variants.stream().map(ItemVariant::getVariantId).collect(Collectors.toList());
        itemVariantAttributeRepository.deleteByVariantIdIn(variantIds);
        itemVariantRepository.deleteByItemId(itemId);
    }

    private ItemVariantResponse toVariantResponse(ItemVariant variant) {
        ItemVariantResponse response = new ItemVariantResponse();
        response.setVariantId(variant.getVariantId());
        response.setItemId(variant.getItemId());
        response.setBranchId(variant.getBranchId());
        response.setSku(variant.getSku());
        response.setDefaultSellingPrice(variant.getDefaultSellingPrice());
        response.setIsActive(variant.getIsActive());
        response.setCreatedAt(variant.getCreatedAt());
        response.setUpdatedAt(variant.getUpdatedAt());
        response.setAttributes(itemVariantAttributeRepository.findByVariantIdOrderByAttributeNameAsc(variant.getVariantId())
                .stream()
                .map(this::toVariantAttributeResponse)
                .collect(Collectors.toList()));
        return response;
    }

    private ItemVariantAttributeResponse toVariantAttributeResponse(ItemVariantAttribute attribute) {
        ItemVariantAttributeResponse response = new ItemVariantAttributeResponse();
        response.setAttributeId(attribute.getAttributeId());
        response.setAttributeName(attribute.getAttributeName());
        response.setAttributeValue(attribute.getAttributeValue());
        return response;
    }
}
