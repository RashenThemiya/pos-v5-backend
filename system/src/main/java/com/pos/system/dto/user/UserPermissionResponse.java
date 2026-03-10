package com.pos.system.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserPermissionResponse {
    private Long permissionId;
    private String code;
    private String description;
    private String module;
}