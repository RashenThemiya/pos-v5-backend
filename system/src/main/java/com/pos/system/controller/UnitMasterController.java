package com.pos.system.controller;

import com.pos.system.dto.common.ApiResponse;
import com.pos.system.dto.unit.UnitRequest;
import com.pos.system.dto.unit.UnitResponse;
import com.pos.system.service.UnitMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/units")
@RequiredArgsConstructor
@CrossOrigin
public class UnitMasterController {

    private final UnitMasterService unitMasterService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<UnitResponse>> create(@Valid @RequestBody UnitRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Unit created successfully", unitMasterService.create(request)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<UnitResponse>>> getAllByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success("Units fetched successfully", unitMasterService.getAllByBranch(branchId)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}/active")
    public ResponseEntity<ApiResponse<List<UnitResponse>>> getActiveByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success("Active units fetched successfully", unitMasterService.getActiveByBranch(branchId)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UnitResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Unit fetched successfully", unitMasterService.getById(id)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UnitResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UnitRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Unit updated successfully", unitMasterService.update(id, request)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_UPDATE')")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<ApiResponse<UnitResponse>> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Unit status toggled successfully", unitMasterService.toggleActive(id)));
    }
}
