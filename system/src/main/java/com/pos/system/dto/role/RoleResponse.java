package com.pos.system.dto.role;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleResponse {
    private Long roleId;
    private String name;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
