package com.pos.system.dto.sale;

import com.pos.system.dto.customer.CustomerResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long orderId;
    private Long branchId;
    private String invoiceNo;
    private String orderNo;
    private Long userId;
    private Long customerId;
    private CustomerResponse customer;
    private Long cashSessionId;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal taxAmount;
    private BigDecimal rounding;
    private BigDecimal total;
    private String paymentStatus;
    private String status;
    private String returnStatus;
    private BigDecimal returnedAmount;
    private LocalDateTime orderDate;
    private String notes;
    private List<OrderProductResponse> items;
    private List<PaymentResponse> payments;
}
