package com.pos.system.controller;

import com.pos.system.dto.supplier.*;
import com.pos.system.service.SupplierManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

    // PURCHASE ORDER

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

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PO_VIEW')")
    @GetMapping("/purchase-orders/branch/{branchId}/item/{itemId}")
    public ResponseEntity<List<PurchaseOrderResponseDto>> getPurchaseOrdersByItem(
            @PathVariable Long branchId, @PathVariable Long itemId) {
        return ResponseEntity.ok(supplierManagementService.getPurchaseOrdersByItem(branchId, itemId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PO_VIEW')")
    @GetMapping("/purchase-orders/branch/{branchId}/paged")
    public ResponseEntity<PurchaseOrderPageResponseDto> searchPurchaseOrdersByBranch(
            @PathVariable Long branchId,
            @ModelAttribute PurchaseOrderSearchRequestDto request,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(supplierManagementService.searchPurchaseOrdersByBranch(branchId, request, pageable));
    }

    // SUPPLY / GRN

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLY_CREATE')")
    @PostMapping("/supplies")
    public ResponseEntity<SupplyResponseDto> createSupply(@RequestBody SupplyRequestDto dto) {
        return ResponseEntity.ok(supplierManagementService.createSupply(dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLY_CREATE')")
    @PostMapping("/manual-grns")
    public ResponseEntity<SupplyResponseDto> createManualGrn(@RequestBody SupplyRequestDto dto) {
        dto.setPoId(null);
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

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLY_VIEW')")
    @GetMapping("/supplies/branch/{branchId}/item/{itemId}")
    public ResponseEntity<List<SupplyResponseDto>> getSuppliesByItem(
            @PathVariable Long branchId, @PathVariable Long itemId) {
        return ResponseEntity.ok(supplierManagementService.getSuppliesByItem(branchId, itemId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLY_VIEW')")
    @GetMapping("/supplies/branch/{branchId}/unpaid")
    public ResponseEntity<List<SupplyResponseDto>> getUnpaidSuppliesByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(supplierManagementService.getUnpaidSuppliesByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLY_VIEW')")
    @GetMapping("/supplies/branch/{branchId}/supplier/{supplierId}/unpaid")
    public ResponseEntity<List<SupplyResponseDto>> getUnpaidSuppliesBySupplier(@PathVariable Long branchId,
                                                                               @PathVariable Long supplierId) {
        return ResponseEntity.ok(supplierManagementService.getUnpaidSuppliesBySupplier(branchId, supplierId));
    }

    // SUPPLIER PAYMENT

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_PAYMENT_CREATE')")
    @PostMapping("/payments")
    public ResponseEntity<SupplierPaymentResponseDto> createSupplierPayment(@RequestBody SupplierPaymentRequestDto dto) {
        return ResponseEntity.ok(supplierManagementService.createSupplierPayment(dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_PAYMENT_VIEW')")
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<SupplierPaymentResponseDto> getSupplierPaymentById(@PathVariable Long paymentId) {
        return ResponseEntity.ok(supplierManagementService.getSupplierPaymentById(paymentId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_PAYMENT_VIEW')")
    @GetMapping("/payments/branch/{branchId}")
    public ResponseEntity<List<SupplierPaymentResponseDto>> getPaymentsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(supplierManagementService.getPaymentsByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_PAYMENT_VIEW')")
    @GetMapping("/payments/branch/{branchId}/supplier/{supplierId}")
    public ResponseEntity<List<SupplierPaymentResponseDto>> getPaymentsBySupplier(@PathVariable Long branchId,
                                                                                  @PathVariable Long supplierId) {
        return ResponseEntity.ok(supplierManagementService.getPaymentsBySupplier(branchId, supplierId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_PAYMENT_VIEW')")
    @GetMapping("/payments/supply/{supplyId}")
    public ResponseEntity<List<SupplierPaymentResponseDto>> getPaymentsBySupply(@PathVariable Long supplyId) {
        return ResponseEntity.ok(supplierManagementService.getPaymentsBySupply(supplyId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_PAYMENT_VIEW')")
    @GetMapping("/payments/purchase-order/{poId}")
    public ResponseEntity<List<SupplierPaymentResponseDto>> getPaymentsByPurchaseOrder(@PathVariable Long poId) {
        return ResponseEntity.ok(supplierManagementService.getPaymentsByPurchaseOrder(poId));
    }

    // PURCHASE RETURNS

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_CREATE')")
    @PostMapping("/purchase-returns")
    public ResponseEntity<PurchaseReturnResponseDto> createPurchaseReturn(@RequestBody PurchaseReturnRequestDto dto) {
        return ResponseEntity.ok(supplierManagementService.createPurchaseReturn(dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_PAYMENT_CREATE')")
    @PostMapping("/purchase-returns/{purchaseReturnId}/refunds")
    public ResponseEntity<PurchaseReturnRefundResponseDto> recordPurchaseReturnRefund(
            @PathVariable Long purchaseReturnId,
            @RequestBody PurchaseReturnRefundRequestDto dto) {
        return ResponseEntity.ok(supplierManagementService.recordPurchaseReturnRefund(purchaseReturnId, dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW')")
    @GetMapping("/purchase-returns/{purchaseReturnId}/refunds")
    public ResponseEntity<List<PurchaseReturnRefundResponseDto>> getPurchaseReturnRefunds(
            @PathVariable Long purchaseReturnId) {
        return ResponseEntity.ok(supplierManagementService.getPurchaseReturnRefunds(purchaseReturnId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW')")
    @GetMapping("/purchase-returns/{purchaseReturnId}")
    public ResponseEntity<PurchaseReturnResponseDto> getPurchaseReturnById(@PathVariable Long purchaseReturnId) {
        return ResponseEntity.ok(supplierManagementService.getPurchaseReturnById(purchaseReturnId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW')")
    @GetMapping("/purchase-returns/branch/{branchId}")
    public ResponseEntity<List<PurchaseReturnResponseDto>> getPurchaseReturnsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(supplierManagementService.getPurchaseReturnsByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW')")
    @GetMapping("/purchase-returns/branch/{branchId}/supplier/{supplierId}")
    public ResponseEntity<List<PurchaseReturnResponseDto>> getPurchaseReturnsBySupplier(@PathVariable Long branchId,
                                                                                        @PathVariable Long supplierId) {
        return ResponseEntity.ok(supplierManagementService.getPurchaseReturnsBySupplier(branchId, supplierId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW')")
    @GetMapping("/purchase-returns/supply/{supplyId}")
    public ResponseEntity<List<PurchaseReturnResponseDto>> getPurchaseReturnsBySupply(@PathVariable Long supplyId) {
        return ResponseEntity.ok(supplierManagementService.getPurchaseReturnsBySupply(supplyId));
    }

    // SUPPLIER LEDGER

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_VIEW')")
    @GetMapping("/branch/{branchId}/supplier/{supplierId}/ledger")
    public ResponseEntity<List<SupplierBalanceTransactionResponseDto>> getSupplierLedger(@PathVariable Long branchId,
                                                                                         @PathVariable Long supplierId) {
        return ResponseEntity.ok(supplierManagementService.getSupplierLedger(branchId, supplierId));
    }

    // SUPPLIER ITEMS

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_UPDATE')")
    @PostMapping("/items")
    public ResponseEntity<SupplierItemResponseDto> addSupplierItem(@RequestBody SupplierItemRequestDto dto) {
        return ResponseEntity.ok(supplierManagementService.addSupplierItem(dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_UPDATE')")
    @PostMapping("/items/bulk")
    public ResponseEntity<List<SupplierItemResponseDto>> addSupplierItemsBulk(
            @RequestBody BulkSupplierItemRequestDto dto) {
        return ResponseEntity.ok(supplierManagementService.addSupplierItemsBulk(dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_UPDATE')")
    @PutMapping("/items/{supplierItemId}")
    public ResponseEntity<SupplierItemResponseDto> updateSupplierItem(@PathVariable Long supplierItemId,
                                                                      @RequestBody SupplierItemRequestDto dto) {
        return ResponseEntity.ok(supplierManagementService.updateSupplierItem(supplierItemId, dto));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_VIEW')")
    @GetMapping("/items/branch/{branchId}/supplier/{supplierId}")
    public ResponseEntity<List<SupplierItemResponseDto>> getSupplierItemsBySupplier(@PathVariable Long branchId,
                                                                                    @PathVariable Long supplierId) {
        return ResponseEntity.ok(supplierManagementService.getSupplierItemsBySupplier(branchId, supplierId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_VIEW')")
    @GetMapping("/items/branch/{branchId}/item/{itemId}")
    public ResponseEntity<List<SupplierItemResponseDto>> getSuppliersByItem(@PathVariable Long branchId,
                                                                            @PathVariable Long itemId) {
        return ResponseEntity.ok(supplierManagementService.getSuppliersByItem(branchId, itemId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_UPDATE')")
    @DeleteMapping("/items/{supplierItemId}")
    public ResponseEntity<String> deleteSupplierItem(@PathVariable Long supplierItemId) {
        supplierManagementService.deleteSupplierItem(supplierItemId);
        return ResponseEntity.ok("Supplier item removed successfully");
    }
}
