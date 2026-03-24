package com.pos.system.controller;

import com.pos.system.dto.category.CategoryRequest;
import com.pos.system.dto.category.CategoryResponse;
import com.pos.system.dto.common.ApiResponse;
import com.pos.system.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@CrossOrigin
public class CategoryController {

    private final CategoryService categoryService;

    // POST /api/categories
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CATEGORY_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Category created successfully", response));
    }

    // GET /api/categories/branch/{branchId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CATEGORY_VIEW')")
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllByBranch(@PathVariable Long branchId) {
        List<CategoryResponse> list = categoryService.getAllByBranch(branchId);
        return ResponseEntity.ok(ApiResponse.success("Categories fetched successfully", list));
    }

    // GET /api/categories/branch/{branchId}/active
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CATEGORY_VIEW')")
    @GetMapping("/branch/{branchId}/active")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getActiveByBranch(@PathVariable Long branchId) {
        List<CategoryResponse> list = categoryService.getActiveByBranch(branchId);
        return ResponseEntity.ok(ApiResponse.success("Active categories fetched successfully", list));
    }

    // GET /api/categories/branch/{branchId}/root
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CATEGORY_VIEW')")
    @GetMapping("/branch/{branchId}/root")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getRootCategories(@PathVariable Long branchId) {
        List<CategoryResponse> list = categoryService.getRootCategoriesByBranch(branchId);
        return ResponseEntity.ok(ApiResponse.success("Root categories fetched successfully", list));
    }

    // GET /api/categories/branch/{branchId}/parent/{parentId}/subcategories
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CATEGORY_VIEW')")
    @GetMapping("/branch/{branchId}/parent/{parentId}/subcategories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getSubCategories(
            @PathVariable Long branchId,
            @PathVariable Long parentId) {
        List<CategoryResponse> list = categoryService.getSubCategories(branchId, parentId);
        return ResponseEntity.ok(ApiResponse.success("Subcategories fetched successfully", list));
    }

    // GET /api/categories/{id}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CATEGORY_VIEW')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(@PathVariable Long id) {
        CategoryResponse response = categoryService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Category fetched successfully", response));
    }

    // PUT /api/categories/{id}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CATEGORY_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", response));
    }

    // PATCH /api/categories/{id}/toggle-active
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CATEGORY_UPDATE')")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<ApiResponse<CategoryResponse>> toggleActive(@PathVariable Long id) {
        CategoryResponse response = categoryService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Category status toggled", response));
    }

    // DELETE /api/categories/{id}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CATEGORY_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully", null));
    }
}
