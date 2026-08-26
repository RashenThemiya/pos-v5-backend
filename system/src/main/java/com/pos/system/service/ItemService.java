package com.pos.system.service;

import com.pos.system.dto.item.ItemSearchRequest;
import com.pos.system.dto.item.ItemRequest;
import com.pos.system.dto.item.ItemResponse;
import com.pos.system.dto.item.ItemUnitRequest;
import com.pos.system.dto.item.ItemUnitResponse;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final UnitMasterRepository unitMasterRepository;
    private final ScaleItemMappingRepository scaleItemMappingRepository;

    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    private final FileStorageService fileStorageService;

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
        unit.setDefaultSellingPrice(request.getDefaultSellingPrice());
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
        unit.setDefaultSellingPrice(request.getDefaultSellingPrice());
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
            brandRepository.findByBrandIdAndBranchId(request.getBrandId(), request.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Brand not found in this branch"));
        }

        ItemUnitRequest unit = buildBaseUnitRequest(request);

        if (unit.getMasterUnitId() == null && (unit.getUnitName() == null || unit.getUnitName().trim().isEmpty())) {
            throw new RuntimeException("Base unit is required");
        }

        if (unit.getMultiplierToBase() == null || unit.getMultiplierToBase().compareTo(BigDecimal.ONE) != 0) {
            throw new RuntimeException("Base unit multiplierToBase must be 1");
        }

        if (unit.getDefaultSellingPrice() == null || unit.getDefaultSellingPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Default selling price must be >= 0");
        }

        if (Boolean.TRUE.equals(request.getIsWeighed())) {
            if (request.getScaleItemCode() == null || request.getScaleItemCode().trim().isEmpty()) {
                throw new RuntimeException("Scale item code is required for weighed item");
            }
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

        if (request.getDefaultSellingPrice() == null || request.getDefaultSellingPrice().compareTo(BigDecimal.ZERO) < 0) {
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

    private ItemUnitRequest buildBaseUnitRequest(ItemRequest request) {
        ItemUnitRequest unit = new ItemUnitRequest();
        unit.setMasterUnitId(request.getBaseMasterUnitId());
        unit.setUnitName(request.getBaseUnitName());
        unit.setMultiplierToBase(new BigDecimal(request.getBaseUnitMultiplierToBase()));
        unit.setBarcode(request.getBaseUnitBarcode());
        unit.setDefaultSellingPrice(new BigDecimal(request.getBaseUnitDefaultSellingPrice()));
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
        unit.setDefaultSellingPrice(request.getDefaultSellingPrice());
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
}
