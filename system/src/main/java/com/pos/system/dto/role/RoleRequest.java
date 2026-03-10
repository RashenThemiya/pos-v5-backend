package com.pos.system.dto.role;

import lombok.Data;

@Data
public class RoleRequest {
    private String name;
    private String description;
    private Boolean isActive;
}