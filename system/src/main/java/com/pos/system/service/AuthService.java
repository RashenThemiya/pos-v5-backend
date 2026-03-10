package com.pos.system.service;

import com.pos.system.dto.auth.ChangePasswordRequest;
import com.pos.system.dto.auth.LoginRequest;
import com.pos.system.dto.auth.RefreshTokenRequest;
import com.pos.system.dto.auth.RegisterRequest;
import com.pos.system.dto.auth.TokenResponse;
import com.pos.system.model.auth.*;
import com.pos.system.repository.AuthSessionRepository;
import com.pos.system.repository.AuthorizationRepository;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.UserRepository;
import com.pos.system.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthorizationRepository authorizationRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public String register(RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new RuntimeException("Username is required");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("Password is required");
        }

        if (authorizationRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && authorizationRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        AuthorizationType type = request.getType() == null
                ? AuthorizationType.CASHIER
                : request.getType();

        if (type == AuthorizationType.SUPERADMIN) {
            if (request.getBranchId() != null) {
                throw new RuntimeException("SUPERADMIN should not have a branch");
            }
        } else {
            if (request.getBranchId() == null) {
                throw new RuntimeException("Branch is required for non-superadmin users");
            }
            if (!branchRepository.existsById(request.getBranchId())) {
                throw new RuntimeException("Branch not found");
            }
        }

        Authorization auth = new Authorization();
        auth.setUsername(request.getUsername().trim());
        auth.setEmail(request.getEmail() != null && !request.getEmail().isBlank()
                ? request.getEmail().trim()
                : null);
        auth.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        auth.setType(type);
        auth.setStatus(AuthorizationStatus.ACTIVE);

        Authorization savedAuth = authorizationRepository.save(auth);

        User user = new User();
        user.setAuthId(savedAuth.getAuthId());
        user.setBranchId(type == AuthorizationType.SUPERADMIN ? null : request.getBranchId());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setIsActive(true);
        userRepository.save(user);

        return "User registered successfully";
    }

    public TokenResponse login(LoginRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new RuntimeException("Username is required");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("Password is required");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        Authorization auth = authorizationRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (auth.getStatus() != AuthorizationStatus.ACTIVE) {
            throw new RuntimeException("Account is not active");
        }

        auth.setLastLoginAt(LocalDateTime.now());
        authorizationRepository.save(auth);

        String accessToken = jwtService.generateAccessToken(auth.getUsername());
        String refreshToken = jwtService.generateRefreshToken(auth.getUsername());

        AuthSession session = new AuthSession();
        session.setAuthId(auth.getAuthId());
        session.setRefreshTokenHash(hash(refreshToken));
        session.setDevice(request.getDevice());
        session.setIpAddress(request.getIpAddress());
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        authSessionRepository.save(session);

        return new TokenResponse(accessToken, refreshToken, "Bearer");
    }

    public TokenResponse refresh(RefreshTokenRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new RuntimeException("Refresh token is required");
        }

        String refreshToken = request.getRefreshToken();
        String username = jwtService.extractUsername(refreshToken);

        if (!jwtService.isTokenValid(refreshToken, username)) {
            throw new RuntimeException("Invalid refresh token");
        }

        AuthSession session = authSessionRepository
                .findByRefreshTokenHashAndRevokedAtIsNull(hash(refreshToken))
                .orElseThrow(() -> new RuntimeException("Refresh session not found"));

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refresh token expired");
        }

        session.setRevokedAt(LocalDateTime.now());
        authSessionRepository.save(session);

        String newAccessToken = jwtService.generateAccessToken(username);
        String newRefreshToken = jwtService.generateRefreshToken(username);

        AuthSession newSession = new AuthSession();
        newSession.setAuthId(session.getAuthId());
        newSession.setRefreshTokenHash(hash(newRefreshToken));
        newSession.setDevice(session.getDevice());
        newSession.setIpAddress(session.getIpAddress());
        newSession.setExpiresAt(LocalDateTime.now().plusDays(7));
        authSessionRepository.save(newSession);

        return new TokenResponse(newAccessToken, newRefreshToken, "Bearer");
    }

    public String changePassword(ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthorized");
        }

        String username = authentication.getName();

        Authorization auth = authorizationRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            throw new RuntimeException("Current password is required");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new RuntimeException("New password is required");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), auth.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        if (request.getNewPassword().length() < 6) {
            throw new RuntimeException("New password must be at least 6 characters");
        }

        auth.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        authorizationRepository.save(auth);

        return "Password changed successfully";
    }

    private String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed");
        }
    }
}