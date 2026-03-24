package com.pos.system.controller;

import com.pos.system.dto.common.ApiResponse;
import com.pos.system.dto.taxrate.ItemTaxRequest;
import com.pos.system.dto.taxrate.TaxRateRequest;
import com.pos.system.dto.taxrate.TaxRateResponse;
import com.pos.system.service.TaxRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin
public class TaxRateController {

    private final TaxRateService taxRateService;

    // ── Tax Rate CRUD ─────────────────────────────────────────────────────────

    // POST /api/tax-rates
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('TAX_CREATE')")
    @PostMapping("/api/tax-rates")
    public ResponseEntity<ApiResponse<TaxRateResponse>> create(@Valid @RequestBody TaxRateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tax rate created successfully", taxRateService.create(request)));
    }

    // GET /api/tax-rates/branch/{branchId}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('TAX_VIEW')")
    @GetMapping("/api/tax-rates/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<TaxRateResponse>>> getAllByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success("Tax rates fetched successfully", taxRateService.getAllByBranch(branchId)));
    }

    // GET /api/tax-rates/branch/{branchId}/active
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('TAX_VIEW')")
    @GetMapping("/api/tax-rates/branch/{branchId}/active")
    public ResponseEntity<ApiResponse<List<TaxRateResponse>>> getActiveByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success("Active tax rates fetched successfully", taxRateService.getActiveByBranch(branchId)));
    }

    // GET /api/tax-rates/{id}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('TAX_VIEW')")
    @GetMapping("/api/tax-rates/{id}")
    public ResponseEntity<ApiResponse<TaxRateResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tax rate fetched successfully", taxRateService.getById(id)));
    }

    // PUT /api/tax-rates/{id}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('TAX_UPDATE')")
    @PutMapping("/api/tax-rates/{id}")
    public ResponseEntity<ApiResponse<TaxRateResponse>> update(@PathVariable Long id, @Valid @RequestBody TaxRateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tax rate updated successfully", taxRateService.update(id, request)));
    }

    // PATCH /api/tax-rates/{id}/toggle-active
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('TAX_UPDATE')")
    @PatchMapping("/api/tax-rates/{id}/toggle-active")
    public ResponseEntity<ApiResponse<TaxRateResponse>> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tax rate status toggled", taxRateService.toggleActive(id)));
    }

    // DELETE /api/tax-rates/{id}
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('TAX_DELETE')")
    @DeleteMapping("/api/tax-rates/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        taxRateService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Tax rate deleted successfully", null));
    }

    // ── Item Tax Assignment ───────────────────────────────────────────────────

    // POST /api/items/{itemId}/taxes  → assign tax to item
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('TAX_UPDATE')")
    @PostMapping("/api/items/{itemId}/taxes")
    public ResponseEntity<ApiResponse<Void>> assignTaxToItem(
            @PathVariable Long itemId,
            @Valid @RequestBody ItemTaxRequest request) {
        taxRateService.assignTaxToItem(itemId, request);
        return ResponseEntity.ok(ApiResponse.success("Tax assigned to item successfully", null));
    }

    // DELETE /api/items/{itemId}/taxes/{taxId}  → remove tax from item
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('TAX_UPDATE')")
    @DeleteMapping("/api/items/{itemId}/taxes/{taxId}")
    public ResponseEntity<ApiResponse<Void>> removeTaxFromItem(
            @PathVariable Long itemId,
            @PathVariable Long taxId) {
        taxRateService.removeTaxFromItem(itemId, taxId);
        return ResponseEntity.ok(ApiResponse.success("Tax removed from item successfully", null));
    }

    // GET /api/items/{itemId}/taxes  → get all taxes for an item
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('TAX_VIEW')")
    @GetMapping("/api/items/{itemId}/taxes")
    public ResponseEntity<ApiResponse<List<TaxRateResponse>>> getTaxesForItem(@PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success("Item taxes fetched successfully", taxRateService.getTaxesForItem(itemId)));
    }
}
