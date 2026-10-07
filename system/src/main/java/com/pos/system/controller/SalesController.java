package com.pos.system.controller;

import com.pos.system.dto.sale.*;
import com.pos.system.dto.sale.SalesReturnAnalyticsResponse.*;
import com.pos.system.service.SalesService;
import com.pos.system.service.SalesReturnAnalyticsService;
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
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@CrossOrigin
public class SalesController {

    private final SalesService salesService;
    private final SalesReturnAnalyticsService salesReturnAnalyticsService;

    // ─── Unified Cashier Sale ─────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_CREATE')")
    @PostMapping("/process")
    public ResponseEntity<OrderResponse> processSale(@RequestBody ProcessSaleRequest request) {
        return ResponseEntity.ok(salesService.processSale(request));
    }

    // ─── Orders ──────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_CREATE')")
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(salesService.createOrder(request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('SALE_CREATE')")
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long orderId) {
        return ResponseEntity.ok(salesService.getOrderById(orderId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('SALE_CREATE')")
    @GetMapping("/orders/invoice/{invoiceNo}")
    public ResponseEntity<OrderResponse> getOrderByInvoiceNo(@PathVariable String invoiceNo) {
        return ResponseEntity.ok(salesService.getOrderByInvoiceNo(invoiceNo));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW')")
    @GetMapping("/orders/branch/{branchId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(salesService.getOrdersByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW')")
    @GetMapping("/orders/branch/{branchId}/paged")
    public ResponseEntity<CustomerOrderPageResponse> getOrdersByBranchPaged(
            @PathVariable Long branchId,
            @PageableDefault(size = 25, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(salesService.getOrdersByBranch(branchId, pageable));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW')")
    @GetMapping("/orders/branch/{branchId}/customer/{customerId}")
    public ResponseEntity<CustomerOrderPageResponse> getCustomerOrdersByBranch(
            @PathVariable Long branchId,
            @PathVariable Long customerId,
            @PageableDefault(size = 25, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(salesService.getCustomerOrdersByBranch(branchId, customerId, pageable));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW')")
    @GetMapping("/orders/branch/{branchId}/item/{itemId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByItem(
            @PathVariable Long branchId, @PathVariable Long itemId) {
        return ResponseEntity.ok(salesService.getOrdersByItem(branchId, itemId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('SALE_CREATE')")
    @GetMapping("/search-products/branch/{branchId}")
    public ResponseEntity<List<SaleProductSearchResponse>> searchProductsForSale(
            @PathVariable Long branchId,
            @RequestParam("q") String query) {
        return ResponseEntity.ok(salesService.searchProductsForSale(branchId, query));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('SALE_CREATE')")
    @GetMapping("/orders/branch/{branchId}/session/{sessionId}")
    public ResponseEntity<List<OrderResponse>> getOrdersBySession(@PathVariable Long branchId,
                                                                   @PathVariable Long sessionId) {
        return ResponseEntity.ok(salesService.getOrdersBySession(branchId, sessionId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW')")
    @GetMapping("/orders/branch/{branchId}/date-range")
    public ResponseEntity<List<OrderResponse>> getOrdersByDateRange(
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(salesService.getOrdersByDateRange(branchId, from, to));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_CANCEL')")
    @PutMapping("/orders/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long orderId,
                                                     @RequestBody CancelOrderRequest request) {
        return ResponseEntity.ok(salesService.cancelOrder(orderId, request));
    }

    // ─── Payments ────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_PAYMENT')")
    @PostMapping("/orders/{orderId}/payments")
    public ResponseEntity<OrderResponse> processPayment(@PathVariable Long orderId,
                                                        @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(salesService.processPayment(orderId, request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW')")
    @GetMapping("/orders/{orderId}/payments")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(salesService.getPaymentsByOrder(orderId));
    }

    // Sales and Return Analytics

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}")
    public ResponseEntity<SalesReturnAnalyticsResponse> getSalesReturnAnalytics(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange,
            @RequestParam(defaultValue = "10") int productLimit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam(defaultValue = "120") int slotMinutes) {

        return ResponseEntity.ok(salesReturnAnalyticsService.getAnalytics(
                branchId, from, to, dateRange, productLimit, startTime, endTime, slotMinutes));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/summary")
    public ResponseEntity<KpiSummary> getSalesReturnAnalyticsSummary(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(salesReturnAnalyticsService.getSummary(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/performance")
    public ResponseEntity<List<PerformanceBucket>> getSalesReturnPerformance(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(salesReturnAnalyticsService.getPerformance(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/customer-engagement")
    public ResponseEntity<CustomerEngagement> getSalesReturnCustomerEngagement(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(salesReturnAnalyticsService.getCustomerEngagement(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/payment-methods")
    public ResponseEntity<PaymentMethods> getSalesReturnPaymentMethods(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(salesReturnAnalyticsService.getPaymentMethods(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/top-products")
    public ResponseEntity<TopProducts> getSalesReturnTopProducts(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(salesReturnAnalyticsService.getTopProducts(branchId, from, to, dateRange, limit));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/return-performance")
    public ResponseEntity<ReturnPerformance> getSalesReturnReturnPerformance(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(salesReturnAnalyticsService.getReturnPerformance(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/activity")
    public ResponseEntity<ActivityResponse> getSalesReturnActivity(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam(defaultValue = "120") int slotMinutes) {

        return ResponseEntity.ok(salesReturnAnalyticsService.getActivity(
                branchId, from, to, dateRange, startTime, endTime, slotMinutes));
    }

    // ─── Returns ─────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_RETURN')")
    @PostMapping("/returns")
    public ResponseEntity<SalesReturnResponse> createReturn(@RequestBody SalesReturnRequest request) {
        return ResponseEntity.ok(salesService.createReturn(request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW')")
    @GetMapping("/returns/{returnId}")
    public ResponseEntity<SalesReturnResponse> getReturnById(@PathVariable Long returnId) {
        return ResponseEntity.ok(salesService.getReturnById(returnId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW')")
    @GetMapping("/return-vouchers/{voucherNoOrCode}")
    public ResponseEntity<ReturnVoucherResponse> getReturnVoucher(@PathVariable String voucherNoOrCode) {
        return ResponseEntity.ok(salesService.getReturnVoucher(voucherNoOrCode));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW')")
    @GetMapping("/returns/order/{orderId}")
    public ResponseEntity<List<SalesReturnResponse>> getReturnsByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(salesService.getReturnsByOrder(orderId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW')")
    @GetMapping("/returns/branch/{branchId}")
    public ResponseEntity<List<SalesReturnResponse>> getReturnsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(salesService.getReturnsByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW')")
    @GetMapping("/returns/branch/{branchId}/paged")
    public ResponseEntity<Page<SalesReturnResponse>> getReturnsByBranchPaged(
            @PathVariable Long branchId,
            @PageableDefault(size = 25, sort = "returnDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(salesService.getReturnsByBranch(branchId, pageable));
    }
}
