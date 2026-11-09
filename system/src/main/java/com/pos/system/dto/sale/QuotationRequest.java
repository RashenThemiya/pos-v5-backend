package com.pos.system.dto.sale;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class QuotationRequest {
    private Long branchId;
    private Long userId;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String customerAddress;
    private LocalDate validUntil;
    private List<QuotationItemRequest> items;
    private List<Long> billPromotionIds;
    private BigDecimal discount;
    private BigDecimal taxAmount;
    private BigDecimal rounding;
    private String notes;
    private String terms;
}
