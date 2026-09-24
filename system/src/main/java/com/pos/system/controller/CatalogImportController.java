package com.pos.system.controller;

import com.pos.system.dto.common.ApiResponse;
import com.pos.system.dto.item.CatalogImportResponse;
import com.pos.system.service.CatalogImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/temp/catalog-import")
@RequiredArgsConstructor
@CrossOrigin
public class CatalogImportController {

    private final CatalogImportService catalogImportService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES')")
    @PostMapping(value = "/{branchId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CatalogImportResponse>> importCatalog(
            @PathVariable Long branchId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean uploadImages) {
        CatalogImportResponse result = catalogImportService.importZip(branchId, file, uploadImages);
        return ResponseEntity.ok(ApiResponse.success("Catalog imported successfully", result));
    }
}
