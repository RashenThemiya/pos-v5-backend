package com.pos.system.dto.authority;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthorityGroupResponse {
    private String module;
    private List<AuthorityResponse> authorities;
}
