package com.pos.system.dto.user;

import com.pos.system.model.auth.AuthorizationStatus;
import com.pos.system.model.auth.AuthorizationType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserResponseDto {

    // ===== USER DETAILS =====
    private Long userId;
    private Long authId;
    private Long branchId;
    private String fullName;
    private String phone;
    private Boolean isActive;

    // ===== AUTHORIZATION DETAILS =====
    private String username;
    private String email;
    private AuthorizationType type;
    private AuthorizationStatus status;
    private String lastLoginAt; // optional (format as string)

    // ===== ROLE DETAILS =====
    private List<String> roles;

    // ===== OPTIONAL (ADVANCED) =====
    // If you want permissions also
    // private List<String> permissions;
}