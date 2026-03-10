package com.pos.system.dto.permission;

import lombok.Data;

@Data
public class PermissionRequest {
    private String code;
    private String description;
    private String module;
}