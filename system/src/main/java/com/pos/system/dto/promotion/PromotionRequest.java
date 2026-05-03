package com.pos.system.dto.promotion;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PromotionRequest {
    private Long branchId;
    private String name;
    private String promoCode;

    // PERCENTAGE | FIXED | ITEM_PERCENTAGE | ITEM_FIXED | BUY_X_GET_Y | BATCH
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
}
