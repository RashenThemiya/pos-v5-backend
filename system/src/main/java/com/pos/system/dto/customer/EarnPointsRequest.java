package com.pos.system.dto.customer;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class EarnPointsRequest {
    private BigDecimal points;
    private BigDecimal valueAmount;
    private String reason;
}