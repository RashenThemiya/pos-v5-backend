package com.pos.system.dto.cash;

import lombok.Data;

@Data
public class CounterRequest {
    private Long branchId;
    private String name;
    private String location;
    private Boolean isActive;
}
