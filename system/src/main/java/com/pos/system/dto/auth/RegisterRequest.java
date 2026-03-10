package com.pos.system.dto.auth;

import com.pos.system.model.auth.AuthorizationType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private AuthorizationType type;
    private String fullName;
    private String phone;
    private Long branchId;
}