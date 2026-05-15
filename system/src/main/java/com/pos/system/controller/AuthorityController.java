package com.pos.system.controller;

import com.pos.system.dto.authority.AuthorityGroupResponse;
import com.pos.system.dto.common.ApiResponse;
import com.pos.system.service.AuthorityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/authorities")
@RequiredArgsConstructor
@CrossOrigin
public class AuthorityController {

    private final AuthorityService authorityService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PERMISSION_VIEW')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AuthorityGroupResponse>>> getAllAuthorities() {
        return ResponseEntity.ok(ApiResponse.success(
                "Authorities fetched successfully",
                authorityService.getAllAuthorities()
        ));
    }
}
