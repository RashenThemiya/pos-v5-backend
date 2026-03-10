package com.pos.system.controller;

import com.pos.system.dto.auth.ChangePasswordRequest;
import com.pos.system.dto.auth.LoginRequest;
import com.pos.system.dto.auth.RefreshTokenRequest;
import com.pos.system.dto.auth.RegisterRequest;
import com.pos.system.dto.auth.TokenResponse;
import com.pos.system.dto.common.ApiResponse;
import com.pos.system.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin
public class AuthController {

    private final AuthService authService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_CREATE')")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(@RequestBody RegisterRequest request) {
        String message = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@RequestBody LoginRequest request) {
        TokenResponse tokenResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", tokenResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@RequestBody RefreshTokenRequest request) {
        TokenResponse tokenResponse = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", tokenResponse));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(@RequestBody ChangePasswordRequest request) {
        String message = authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }
}