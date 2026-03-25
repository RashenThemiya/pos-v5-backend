package com.pos.system.controller;

import com.pos.system.dto.brand.BrandRequest;
import com.pos.system.dto.brand.BrandResponse;
import com.pos.system.dto.common.ApiResponse;
import com.pos.system.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
@CrossOrigin
public class BrandController {

    private final BrandService brandService;

    // POST /api/brands
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('BRAND_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<BrandResponse>> create(@Valid @RequestBody BrandRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Brand created successfully", brandService.create(request)));
    }

    // GET /api/brands/branch/{branchId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('BRAND_VIEW')")
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getAllByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success("Brands fetched successfully", brandService.getAllByBranch(branchId)));
    }

    // GET /api/brands/branch/{branchId}/active
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('BRAND_VIEW')")
    @GetMapping("/branch/{branchId}/active")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getActiveByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success("Active brands fetched successfully", brandService.getActiveByBranch(branchId)));
    }

    // GET /api/brands/{id}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('BRAND_VIEW')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Brand fetched successfully", brandService.getById(id)));
    }

    // PUT /api/brands/{id}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('BRAND_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> update(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Brand updated successfully", brandService.update(id, request)));
    }

    // PATCH /api/brands/{id}/toggle-active
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('BRAND_UPDATE')")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<ApiResponse<BrandResponse>> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Brand status toggled", brandService.toggleActive(id)));
    }

    // DELETE /api/brands/{id}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('BRAND_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        brandService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Brand deleted successfully", null));
    }
}
