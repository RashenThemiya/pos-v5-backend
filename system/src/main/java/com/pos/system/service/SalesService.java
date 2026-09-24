package com.pos.system.service;

import com.pos.system.dto.sale.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface SalesService {

    // Unified cashier sale (order + payment in one shot)
    OrderResponse processSale(ProcessSaleRequest request);

    // Orders
    OrderResponse createOrder(CreateOrderRequest request);
    OrderResponse getOrderById(Long orderId);
    OrderResponse getOrderByInvoiceNo(String invoiceNo);
    List<OrderResponse> getOrdersByBranch(Long branchId);
    CustomerOrderPageResponse getOrdersByBranch(Long branchId, Pageable pageable);
    CustomerOrderPageResponse getCustomerOrdersByBranch(Long branchId, Long customerId, Pageable pageable);
    List<OrderResponse> getOrdersByItem(Long branchId, Long itemId);
    List<OrderResponse> getOrdersBySession(Long branchId, Long sessionId);
    List<OrderResponse> getOrdersByDateRange(Long branchId, LocalDateTime from, LocalDateTime to);
    OrderResponse cancelOrder(Long orderId, CancelOrderRequest request);
    List<SaleProductSearchResponse> searchProductsForSale(Long branchId, String query);

    // Payments
    OrderResponse processPayment(Long orderId, PaymentRequest request);
    List<PaymentResponse> getPaymentsByOrder(Long orderId);

    // Returns
    SalesReturnResponse createReturn(SalesReturnRequest request);
    SalesReturnResponse getReturnById(Long returnId);
    List<SalesReturnResponse> getReturnsByOrder(Long orderId);
    List<SalesReturnResponse> getReturnsByBranch(Long branchId);
    ReturnVoucherResponse getReturnVoucher(String voucherNoOrCode);
    Page<SalesReturnResponse> getReturnsByBranch(Long branchId, Pageable pageable);
}
