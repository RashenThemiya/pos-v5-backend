package com.pos.system.service;

import com.pos.system.dto.sale.*;

import java.time.LocalDateTime;
import java.util.List;

public interface SalesService {

    // Orders
    OrderResponse createOrder(CreateOrderRequest request);
    OrderResponse getOrderById(Long orderId);
    OrderResponse getOrderByInvoiceNo(String invoiceNo);
    List<OrderResponse> getOrdersByBranch(Long branchId);
    List<OrderResponse> getOrdersBySession(Long branchId, Long sessionId);
    List<OrderResponse> getOrdersByDateRange(Long branchId, LocalDateTime from, LocalDateTime to);
    OrderResponse cancelOrder(Long orderId, CancelOrderRequest request);

    // Payments
    OrderResponse processPayment(Long orderId, PaymentRequest request);
    List<PaymentResponse> getPaymentsByOrder(Long orderId);

    // Returns
    SalesReturnResponse createReturn(SalesReturnRequest request);
    SalesReturnResponse getReturnById(Long returnId);
    List<SalesReturnResponse> getReturnsByOrder(Long orderId);
    List<SalesReturnResponse> getReturnsByBranch(Long branchId);
}
