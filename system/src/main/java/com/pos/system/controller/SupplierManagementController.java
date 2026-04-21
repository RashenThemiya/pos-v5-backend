package com.pos.system.controller;

import com.pos.system.dto.supplier.*;
import com.pos.system.service.SupplierManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@CrossOrigin
public class SupplierManagementController {

    private final SupplierManagementService supplierManagementService;

    // =========================
    // SUPPLIER
    // =========================

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_CREATE')")
    @PostMapping
    public ResponseEntity<SupplierResponseDto> createSupplier(@RequestBody SupplierRequestDto dto) {
        return ResponseEntity.ok(supplierManagementService.createSupplier(dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_UPDATE')")
    @PutMapping("/{supplierId}")
    public ResponseEntity<SupplierResponseDto> updateSupplier(@PathVariable Long supplierId,
                                                              @RequestBody SupplierRequestDto dto) {
        return ResponseEntity.ok(supplierManagementService.updateSupplier(supplierId, dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_VIEW')")
    @GetMapping("/{supplierId}")
    public ResponseEntity<SupplierResponseDto> getSupplierById(@PathVariable Long supplierId) {
        return ResponseEntity.ok(supplierManagementService.getSupplierById(supplierId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_VIEW')")
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<SupplierResponseDto>> getSuppliersByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(supplierManagementService.getSuppliersByBranch(branchId));
    }

    // =========================
    // PURCHASE ORDER
    // =========================

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PO_CREATE')")
    @PostMapping("/purchase-orders")
    public ResponseEntity<PurchaseOrderResponseDto> createPurchaseOrder(@RequestBody PurchaseOrderRequestDto dto) {
        return ResponseEntity.ok(supplierManagementService.createPurchaseOrder(dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PO_VIEW')")
    @GetMapping("/purchase-orders/{poId}")
    public ResponseEntity<PurchaseOrderResponseDto> getPurchaseOrderById(@PathVariable Long poId) {
        return ResponseEntity.ok(supplierManagementService.getPurchaseOrderById(poId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PO_VIEW')")
    @GetMapping("/purchase-orders/branch/{branchId}")
    public ResponseEntity<List<PurchaseOrderResponseDto>> getPurchaseOrdersByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(supplierManagementService.getPurchaseOrdersByBranch(branchId));
    }

    // =========================
    // SUPPLY (GRN)
    // =========================

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLY_CREATE')")
    @PostMapping("/supplies")
    public ResponseEntity<SupplyResponseDto> createSupply(@RequestBody SupplyRequestDto dto) {
        return ResponseEntity.ok(supplierManagementService.createSupply(dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLY_VIEW')")
    @GetMapping("/supplies/{supplyId}")
    public ResponseEntity<SupplyResponseDto> getSupplyById(@PathVariable Long supplyId) {
        return ResponseEntity.ok(supplierManagementService.getSupplyById(supplyId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLY_VIEW')")
    @GetMapping("/supplies/branch/{branchId}")
    public ResponseEntity<List<SupplyResponseDto>> getSuppliesByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(supplierManagementService.getSuppliesByBranch(branchId));
    }

    // =========================
    // SUPPLIER PAYMENT
    // =========================

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_PAYMENT_CREATE')")
    @PostMapping("/payments")
    public ResponseEntity<SupplierPaymentResponseDto> createSupplierPayment(@RequestBody SupplierPaymentRequestDto dto) {
        return ResponseEntity.ok(supplierManagementService.createSupplierPayment(dto));
    }
}