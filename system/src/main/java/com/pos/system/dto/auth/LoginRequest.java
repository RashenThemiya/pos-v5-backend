package com.pos.system.dto.auth;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
    private String device;
    private String ipAddress;
}