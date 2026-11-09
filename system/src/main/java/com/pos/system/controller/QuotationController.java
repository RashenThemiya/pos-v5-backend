package com.pos.system.controller;

import com.pos.system.dto.sale.QuotationRequest;
import com.pos.system.dto.sale.QuotationResponse;
import com.pos.system.dto.sale.QuotationStatusRequest;
import com.pos.system.service.QuotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotations")
@RequiredArgsConstructor
@CrossOrigin
public class QuotationController {
    private final QuotationService quotationService;

    @PostMapping
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_CREATE')")
    public ResponseEntity<QuotationResponse> create(@RequestBody QuotationRequest request) {
        return ResponseEntity.ok(quotationService.create(request));
    }

    @PutMapping("/{quotationId}")
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_CREATE')")
    public ResponseEntity<QuotationResponse> update(@PathVariable Long quotationId, @RequestBody QuotationRequest request) {
        return ResponseEntity.ok(quotationService.update(quotationId, request));
    }

    @GetMapping("/{quotationId}")
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('SALE_CREATE')")
    public ResponseEntity<QuotationResponse> get(@PathVariable Long quotationId) {
        return ResponseEntity.ok(quotationService.getById(quotationId));
    }

    @GetMapping("/number/{quotationNo}")
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('SALE_CREATE')")
    public ResponseEntity<QuotationResponse> getByNumber(@PathVariable String quotationNo) {
        return ResponseEntity.ok(quotationService.getByNumber(quotationNo));
    }

    @GetMapping("/branch/{branchId}")
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('SALE_CREATE')")
    public ResponseEntity<Page<QuotationResponse>> list(@PathVariable Long branchId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(quotationService.list(branchId, status, customerId, pageable));
    }

    @PatchMapping("/{quotationId}/status")
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_CREATE')")
    public ResponseEntity<QuotationResponse> updateStatus(@PathVariable Long quotationId, @RequestBody QuotationStatusRequest request) {
        return ResponseEntity.ok(quotationService.updateStatus(quotationId, request.getStatus()));
    }
}
