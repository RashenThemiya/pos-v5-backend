package com.pos.system.controller;

import com.pos.system.dto.supplier.*;
import com.pos.system.dto.supplier.PurchaseOrderAnalyticsResponse.*;
import com.pos.system.dto.supplier.PurchaseReturnAnalyticsResponse.*;
import com.pos.system.service.PurchaseOrderAnalyticsService;
import com.pos.system.service.PurchaseReturnAnalyticsService;
import com.pos.system.service.SupplierManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@CrossOrigin
public class SupplierManagementController {

    private final SupplierManagementService supplierManagementService;
    private final PurchaseOrderAnalyticsService purchaseOrderAnalyticsService;
    private final PurchaseReturnAnalyticsService purchaseReturnAnalyticsService;

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

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SUPPLIER_VIEW')")
    @GetMapping("/branch/{branchId}/paged")
    public ResponseEntity<Page<SupplierResponseDto>> searchSuppliersByBranch(
            @PathVariable Long branchId,
            @ModelAttribute SupplierSearchRequestDto request,
            @PageableDefault(size = 25, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(supplierManagementService.searchSuppliersByBranch(branchId, request, pageable));
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

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PO_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-orders/analytics/branch/{branchId}")
    public ResponseEntity<PurchaseOrderAnalyticsResponse> getPurchaseOrderAnalytics(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(purchaseOrderAnalyticsService.getAnalytics(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PO_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-orders/analytics/branch/{branchId}/summary")
    public ResponseEntity<Summary> getPurchaseOrderAnalyticsSummary(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(purchaseOrderAnalyticsService.getSummary(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PO_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-orders/analytics/branch/{branchId}/spend-trend")
    public ResponseEntity<List<SpendBucket>> getPurchaseOrderSpendTrend(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(purchaseOrderAnalyticsService.getSpendTrend(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PO_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-orders/analytics/branch/{branchId}/status")
    public ResponseEntity<StatusBreakdown> getPurchaseOrderAnalyticsStatus(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(purchaseOrderAnalyticsService.getStatus(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PO_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-orders/analytics/branch/{branchId}/receiving-performance")
    public ResponseEntity<ReceivingPerformance> getPurchaseOrderReceivingPerformance(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(purchaseOrderAnalyticsService.getReceivingPerformance(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PO_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-orders/analytics/branch/{branchId}/value-distribution")
    public ResponseEntity<List<ValueDistribution>> getPurchaseOrderValueDistribution(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(purchaseOrderAnalyticsService.getValueDistribution(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PO_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-orders/analytics/branch/{branchId}/payment-exposure")
    public ResponseEntity<PaymentExposure> getPurchaseOrderPaymentExposure(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(purchaseOrderAnalyticsService.getPaymentExposure(branchId, from, to, dateRange));
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
    @GetMapping("/supplies/branch/{branchId}/paged")
    public ResponseEntity<SupplyPageResponseDto> searchSuppliesByBranch(
            @PathVariable Long branchId,
            @ModelAttribute SupplySearchRequestDto request,
            @PageableDefault(size = 25, sort = "supplyDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(supplierManagementService.searchSuppliesByBranch(branchId, request, pageable));
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
    @GetMapping("/payments/branch/{branchId}/paged")
    public ResponseEntity<SupplierPaymentPageResponseDto> searchPaymentsByBranch(
            @PathVariable Long branchId,
            @ModelAttribute SupplierPaymentSearchRequestDto request,
            @PageableDefault(size = 25, sort = "paymentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(supplierManagementService.searchPaymentsByBranch(branchId, request, pageable));
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
    @GetMapping("/purchase-returns/branch/{branchId}/paged")
    public ResponseEntity<PurchaseReturnPageResponseDto> searchPurchaseReturnsByBranch(
            @PathVariable Long branchId,
            @ModelAttribute PurchaseReturnSearchRequestDto request,
            @PageableDefault(size = 25, sort = "returnDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(supplierManagementService.searchPurchaseReturnsByBranch(branchId, request, pageable));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-returns/analytics/branch/{branchId}")
    public ResponseEntity<PurchaseReturnAnalyticsResponse> getPurchaseReturnAnalytics(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange,
            @RequestParam(defaultValue = "10") int supplierLimit,
            @RequestParam(defaultValue = "10") int itemLimit) {

        return ResponseEntity.ok(purchaseReturnAnalyticsService.getAnalytics(
                branchId, from, to, dateRange, supplierLimit, itemLimit));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-returns/analytics/branch/{branchId}/summary")
    public ResponseEntity<ReturnSummary> getPurchaseReturnAnalyticsSummary(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(purchaseReturnAnalyticsService.getSummary(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-returns/analytics/branch/{branchId}/trend")
    public ResponseEntity<List<TrendBucket>> getPurchaseReturnTrend(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(purchaseReturnAnalyticsService.getTrend(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-returns/analytics/branch/{branchId}/return-status")
    public ResponseEntity<List<ReturnStatusEntry>> getPurchaseReturnStatusAnalytics(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(purchaseReturnAnalyticsService.getReturnStatus(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-returns/analytics/branch/{branchId}/refund-status")
    public ResponseEntity<RefundStatus> getPurchaseReturnRefundStatus(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(purchaseReturnAnalyticsService.getRefundStatus(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-returns/analytics/branch/{branchId}/supplier-analysis")
    public ResponseEntity<List<SupplierReturnEntry>> getPurchaseReturnSupplierAnalysis(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(purchaseReturnAnalyticsService.getSupplierAnalysis(
                branchId, from, to, dateRange, limit));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-returns/analytics/branch/{branchId}/pending-aging")
    public ResponseEntity<List<AgingBucket>> getPurchaseReturnPendingAging(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(purchaseReturnAnalyticsService.getPendingAging(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PURCHASE_RETURN_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/purchase-returns/analytics/branch/{branchId}/most-returned-items")
    public ResponseEntity<List<ReturnedItemEntry>> getMostReturnedPurchaseItems(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(purchaseReturnAnalyticsService.getMostReturnedItems(
                branchId, from, to, dateRange, limit));
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
