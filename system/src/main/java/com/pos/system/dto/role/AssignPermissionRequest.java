package com.pos.system.dto.role;

import lombok.Data;

import java.util.List;

@Data
public class AssignPermissionRequest {
    private String authorityCode;
    private List<String> authorityCodes;
}
