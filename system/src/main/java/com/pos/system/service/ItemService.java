package com.pos.system.service;

import com.pos.system.dto.item.ItemRequest;
import com.pos.system.dto.item.ItemResponse;
import com.pos.system.model.catalog.Item;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.BrandRepository;
import com.pos.system.repository.CategoryRepository;
import com.pos.system.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public ItemResponse create(ItemRequest request) {
        // Validate branch
        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + request.getBranchId()));

        // Check duplicate SKU within branch
        if (itemRepository.existsByBranchIdAndSku(request.getBranchId(), request.getSku())) {
            throw new RuntimeException("SKU already exists in this branch: " + request.getSku());
        }

        // Validate category if provided
        if (request.getCategoryId() != null) {
            categoryRepository.findByCategoryIdAndBranchId(request.getCategoryId(), request.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Category not found in this branch"));
        }

        // Validate brand if provided
        if (request.getBrandId() != null) {
            brandRepository.findByBrandIdAndBranchId(request.getBrandId(), request.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Brand not found in this branch"));
        }

        Item item = new Item();
        mapRequestToItem(request, item);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        return toResponse(itemRepository.save(item));
    }

    public List<ItemResponse> getAllByBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        return itemRepository.findByBranchId(branchId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<ItemResponse> getActiveByBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        return itemRepository.findByBranchIdAndIsActive(branchId, true)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<ItemResponse> getByCategory(Long branchId, Long categoryId) {
        return itemRepository.findByBranchIdAndCategoryId(branchId, categoryId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<ItemResponse> getByBrand(Long branchId, Long brandId) {
        return itemRepository.findByBranchIdAndBrandId(branchId, brandId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ItemResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public ItemResponse getBySku(Long branchId, String sku) {
        Item item = itemRepository.findByBranchIdAndSku(branchId, sku)
                .orElseThrow(() -> new RuntimeException("Item not found with SKU: " + sku));
        return toResponse(item);
    }

    public ItemResponse update(Long id, ItemRequest request) {
        Item item = findById(id);

        // If SKU changed, check for duplicates
        if (!item.getSku().equals(request.getSku()) &&
                itemRepository.existsByBranchIdAndSku(item.getBranchId(), request.getSku())) {
            throw new RuntimeException("SKU already exists in this branch: " + request.getSku());
        }

        // Validate category if provided
        if (request.getCategoryId() != null) {
            categoryRepository.findByCategoryIdAndBranchId(request.getCategoryId(), item.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Category not found in this branch"));
        }

        // Validate brand if provided
        if (request.getBrandId() != null) {
            brandRepository.findByBrandIdAndBranchId(request.getBrandId(), item.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Brand not found in this branch"));
        }

        mapRequestToItem(request, item);
        item.setUpdatedAt(LocalDateTime.now());

        return toResponse(itemRepository.save(item));
    }

    public void delete(Long id) {
        Item item = findById(id);
        itemRepository.delete(item);
    }

    public ItemResponse toggleActive(Long id) {
        Item item = findById(id);
        item.setIsActive(!item.getIsActive());
        item.setUpdatedAt(LocalDateTime.now());
        return toResponse(itemRepository.save(item));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Item findById(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Item not found with id: " + id));
    }

    private void mapRequestToItem(ItemRequest request, Item item) {
        item.setBranchId(request.getBranchId());
        item.setSku(request.getSku());
        item.setName(request.getName());
        item.setImage(request.getImage());
        item.setCategoryId(request.getCategoryId());
        item.setBrandId(request.getBrandId());
        item.setIsWeighed(request.getIsWeighed() != null ? request.getIsWeighed() : false);
        item.setScaleBarcodePrefix(request.getScaleBarcodePrefix());
        item.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
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
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());

        // Resolve category name
        if (item.getCategoryId() != null) {
            categoryRepository.findById(item.getCategoryId())
                    .ifPresent(cat -> response.setCategoryName(cat.getName()));
        }

        // Resolve brand name
        if (item.getBrandId() != null) {
            brandRepository.findById(item.getBrandId())
                    .ifPresent(brand -> response.setBrandName(brand.getName()));
        }

        return response;
    }
}
