package com.pos.system.dto.sale;

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
    private Long cashSessionId;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal taxAmount;
    private BigDecimal rounding;
    private BigDecimal total;
    private String paymentStatus;
    /** Sum of payments applied to the order. */
    private BigDecimal amountPaid;
    /** Order value written off by returns made while the order was not fully paid. */
    private BigDecimal returnBalanceAdjustment;
    /** Amount given back through returns: paid out plus credited to the customer account. */
    private BigDecimal refundedAmount;
    /** Part of amountPaid that was charged to the customer's credit account. */
    private BigDecimal creditPaidAmount;
    /** Credit-account payments already reversed by returns. */
    private BigDecimal creditReversedAmount;
    /** What the customer still owes: total - returnBalanceAdjustment - amountPaid. */
    private BigDecimal balanceDue;
    private String status;
    private LocalDateTime orderDate;
    private String notes;
    private List<OrderProductResponse> items;
    private List<PaymentResponse> payments;
    private List<AppliedPromotionResponse> appliedPromotions;
    private BigDecimal promotionDiscount;
}
