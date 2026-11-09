package com.pos.system.dto.sale;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class QuotationResponse {
    private Long quotationId;
    private String quotationNo;
    private Long branchId;
    private Long userId;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String customerAddress;
    private String status;
    private LocalDate validUntil;
    private BigDecimal subtotal;
    private BigDecimal itemDiscount;
    private BigDecimal discount;
    private BigDecimal totalDiscount;
    private BigDecimal taxAmount;
    private BigDecimal rounding;
    private BigDecimal total;
    private String notes;
    private String terms;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<QuotationItemResponse> items;
    private List<AppliedPromotionResponse> appliedPromotions;
}
