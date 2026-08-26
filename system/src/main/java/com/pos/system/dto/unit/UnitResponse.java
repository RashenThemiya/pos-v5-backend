package com.pos.system.dto.unit;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UnitResponse {
    private Long unitId;
    private Long branchId;
    private String name;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
