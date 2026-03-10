package com.pos.system.dto.user;

import com.pos.system.model.auth.AuthorizationType;
import lombok.Data;

@Data
public class CreateUserRequest {
    private String username;
    private String email;
    private String password;
    private AuthorizationType type;
    private String fullName;
    private String phone;
    private Long branchId;
}