package com.pos.system.dto.sale;

import lombok.Data;

@Data
public class CancelOrderRequest {
    private Long cancelledBy;
    private String cancelReason;
}
