package com.pos.system.controller;

import com.pos.system.dto.common.ApiResponse;
import com.pos.system.dto.item.ItemRequest;
import com.pos.system.dto.item.ItemResponse;
import com.pos.system.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    // POST /api/items
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<ItemResponse>> create(@Valid @RequestBody ItemRequest request) {
        ItemResponse response = itemService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Item created successfully", response));
    }

    // GET /api/items/branch/{branchId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> getAllByBranch(@PathVariable Long branchId) {
        List<ItemResponse> list = itemService.getAllByBranch(branchId);
        return ResponseEntity.ok(ApiResponse.success("Items fetched successfully", list));
    }

    // GET /api/items/branch/{branchId}/active
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}/active")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> getActiveByBranch(@PathVariable Long branchId) {
        List<ItemResponse> list = itemService.getActiveByBranch(branchId);
        return ResponseEntity.ok(ApiResponse.success("Active items fetched successfully", list));
    }

    // GET /api/items/branch/{branchId}/category/{categoryId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> getByCategory(
            @PathVariable Long branchId,
            @PathVariable Long categoryId) {
        List<ItemResponse> list = itemService.getByCategory(branchId, categoryId);
        return ResponseEntity.ok(ApiResponse.success("Items by category fetched successfully", list));
    }

    // GET /api/items/branch/{branchId}/brand/{brandId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}/brand/{brandId}")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> getByBrand(
            @PathVariable Long branchId,
            @PathVariable Long brandId) {
        List<ItemResponse> list = itemService.getByBrand(branchId, brandId);
        return ResponseEntity.ok(ApiResponse.success("Items by brand fetched successfully", list));
    }

    // GET /api/items/{id}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ItemResponse>> getById(@PathVariable Long id) {
        ItemResponse response = itemService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Item fetched successfully", response));
    }

    // GET /api/items/branch/{branchId}/sku/{sku}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}/sku/{sku}")
    public ResponseEntity<ApiResponse<ItemResponse>> getBySku(
            @PathVariable Long branchId,
            @PathVariable String sku) {
        ItemResponse response = itemService.getBySku(branchId, sku);
        return ResponseEntity.ok(ApiResponse.success("Item fetched by SKU", response));
    }

    // PUT /api/items/{id}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ItemResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ItemRequest request) {
        ItemResponse response = itemService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Item updated successfully", response));
    }

    // PATCH /api/items/{id}/toggle-active
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_UPDATE')")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<ApiResponse<ItemResponse>> toggleActive(@PathVariable Long id) {
        ItemResponse response = itemService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Item status toggled", response));
    }

    // DELETE /api/items/{id}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        itemService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Item deleted successfully", null));
    }
}
