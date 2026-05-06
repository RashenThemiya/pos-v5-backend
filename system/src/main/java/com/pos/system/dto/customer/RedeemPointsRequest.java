package com.pos.system.dto.customer;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RedeemPointsRequest {
    private BigDecimal points;
    private String reason;
}