package com.pos.system.controller;

import com.pos.system.dto.sale.*;
import com.pos.system.service.SalesService;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@CrossOrigin
public class SalesController {

    private final SalesService salesService;

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
    @GetMapping("/returns/order/{orderId}")
    public ResponseEntity<List<SalesReturnResponse>> getReturnsByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(salesService.getReturnsByOrder(orderId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW')")
    @GetMapping("/returns/branch/{branchId}")
    public ResponseEntity<List<SalesReturnResponse>> getReturnsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(salesService.getReturnsByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SALE_VIEW') or hasAuthority('SALE_CREATE')")
    @GetMapping("/return-vouchers/{voucherNoOrCode}")
    public ResponseEntity<ReturnVoucherResponse> getReturnVoucher(@PathVariable String voucherNoOrCode) {
        return ResponseEntity.ok(salesService.getReturnVoucher(voucherNoOrCode));
    }
}
