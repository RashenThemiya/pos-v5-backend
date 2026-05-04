package com.pos.system.dto.customer;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AddBalanceRequest {
    private BigDecimal amount;
    private String reason;
}