package com.pos.system.dto.promotion;

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
public class PromotionResponse {
    private Long promotionId;
    private Long branchId;
    private String name;
    private String promoCode;
    private String type;
    private BigDecimal value;
    private BigDecimal minBillTotal;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer priority;
    private Integer maxUsesTotal;
    private Integer maxUsesPerCustomer;
    private Boolean isStackable;
    private Boolean isActive;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long totalRedemptions;

    // populated on detail view
    private List<PromotionItemResponse> items;
    private List<PromotionBatchResponse> batches;
    private List<BuyXGetYRuleResponse> buyXGetYRules;
}
