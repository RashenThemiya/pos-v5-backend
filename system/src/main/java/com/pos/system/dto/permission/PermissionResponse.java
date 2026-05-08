package com.pos.system.dto.permission;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PermissionResponse {
    private Long permissionId;
    private String code;
    private String description;
    private String module;
    private LocalDateTime createdAt;
}
