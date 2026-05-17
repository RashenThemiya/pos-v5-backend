package com.pos.system.controller;

import com.pos.system.dto.common.ApiResponse;
import com.pos.system.dto.scale.ScaleBarcodeSettingRequest;
import com.pos.system.dto.scale.ScaleBarcodeSettingResponse;
import com.pos.system.service.ScaleBarcodeSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scale-barcode-settings")
@RequiredArgsConstructor
@CrossOrigin
public class ScaleBarcodeSettingController {

    private final ScaleBarcodeSettingService scaleBarcodeSettingService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<ScaleBarcodeSettingResponse>> create(
            @Valid @RequestBody ScaleBarcodeSettingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Scale barcode setting created successfully",
                scaleBarcodeSettingService.create(request)
        ));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<ScaleBarcodeSettingResponse>>> getByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Scale barcode settings fetched successfully",
                scaleBarcodeSettingService.getByBranch(branchId)
        ));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ScaleBarcodeSettingResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Scale barcode setting fetched successfully",
                scaleBarcodeSettingService.getById(id)
        ));
    }
}
