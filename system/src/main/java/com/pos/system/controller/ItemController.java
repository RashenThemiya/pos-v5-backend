package com.pos.system.controller;

import com.pos.system.dto.common.ApiResponse;
import com.pos.system.dto.item.ItemRequest;
import com.pos.system.dto.item.ItemResponse;
import com.pos.system.dto.item.ItemUnitRequest;
import com.pos.system.dto.item.ItemUnitResponse;
import com.pos.system.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
@CrossOrigin
public class ItemController {

    private final ItemService itemService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_CREATE')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ItemResponse>> create(@Valid @ModelAttribute ItemRequest request) {
        ItemResponse response = itemService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Item created successfully", response));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> getAllByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success("Items fetched successfully", itemService.getAllByBranch(branchId)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}/active")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> getActiveByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success("Active items fetched successfully", itemService.getActiveByBranch(branchId)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> getByCategory(
            @PathVariable Long branchId,
            @PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success("Items by category fetched successfully", itemService.getByCategory(branchId, categoryId)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}/brand/{brandId}")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> getByBrand(
            @PathVariable Long branchId,
            @PathVariable Long brandId) {
        return ResponseEntity.ok(ApiResponse.success("Items by brand fetched successfully", itemService.getByBrand(branchId, brandId)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ItemResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Item fetched successfully", itemService.getById(id)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}/sku/{sku}")
    public ResponseEntity<ApiResponse<ItemResponse>> getBySku(
            @PathVariable Long branchId,
            @PathVariable String sku) {
        return ResponseEntity.ok(ApiResponse.success("Item fetched by SKU successfully", itemService.getBySku(branchId, sku)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}/barcode/{barcode}")
    public ResponseEntity<ApiResponse<ItemResponse>> getByBarcode(
            @PathVariable Long branchId,
            @PathVariable String barcode) {
        return ResponseEntity.ok(ApiResponse.success("Item fetched by barcode successfully", itemService.getByBarcode(branchId, barcode)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_UPDATE')")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ItemResponse>> update(
            @PathVariable Long id,
            @Valid @ModelAttribute ItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Item updated successfully", itemService.update(id, request)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_UPDATE')")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<ApiResponse<ItemResponse>> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Item status toggled successfully", itemService.toggleActive(id)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        itemService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Item deleted successfully", null));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/{itemId}/units")
    public ResponseEntity<ApiResponse<List<ItemUnitResponse>>> getUnitsByItem(@PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success("Item units fetched successfully", itemService.getUnitsByItem(itemId)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_CREATE')")
    @PostMapping("/{itemId}/units")
    public ResponseEntity<ApiResponse<ItemUnitResponse>> addUnit(
            @PathVariable Long itemId,
            @Valid @RequestBody ItemUnitRequest request,
            @RequestParam(defaultValue = "false") Boolean autoGenerateBarcode) {
        return ResponseEntity.ok(ApiResponse.success("Item unit created successfully", itemService.addUnit(itemId, request, autoGenerateBarcode)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_UPDATE')")
    @PutMapping("/{itemId}/units/{unitId}")
    public ResponseEntity<ApiResponse<ItemUnitResponse>> updateUnit(
            @PathVariable Long itemId,
            @PathVariable Long unitId,
            @Valid @RequestBody ItemUnitRequest request,
            @RequestParam(defaultValue = "false") Boolean autoGenerateBarcode) {
        return ResponseEntity.ok(ApiResponse.success("Item unit updated successfully", itemService.updateUnit(itemId, unitId, request, autoGenerateBarcode)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_DELETE')")
    @DeleteMapping("/{itemId}/units/{unitId}")
    public ResponseEntity<ApiResponse<Void>> deleteUnit(
            @PathVariable Long itemId,
            @PathVariable Long unitId) {
        itemService.deleteUnit(itemId, unitId);
        return ResponseEntity.ok(ApiResponse.success("Item unit deleted successfully", null));
    }
}