package com.pos.system.dto.unit;

import lombok.Data;

@Data
public class UnitRequest {
    private Long branchId;
    private String name;
    private Boolean isActive;
}
