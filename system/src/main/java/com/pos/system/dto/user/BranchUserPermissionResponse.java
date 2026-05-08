package com.pos.system.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchUserPermissionResponse {
    private Long userId;
    private String fullName;
    private Long authId;
    private String username;
    private List<String> roles;
    private List<UserPermissionResponse> permissions;
}