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
public class CustomerOrderViewResponse {
    private Long orderId;
    private String invoiceNo;
    private String orderNo;
    private LocalDateTime createdAt;
    private String paymentMethod;
    private List<CustomerOrderItemResponse> items;
    private BigDecimal itemCount;
    private BigDecimal totalAmount;
    private String status;
}
