package com.pos.system.security;

import com.pos.system.model.auth.Authorization;
import com.pos.system.model.auth.AuthorizationStatus;
import com.pos.system.model.auth.AuthorizationType;
import com.pos.system.model.auth.RolePermission;
import com.pos.system.model.auth.User;
import com.pos.system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AuthorizationRepository authorizationRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Authorization auth = authorizationRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + auth.getType().name()));

        if (auth.getType() == AuthorizationType.SUPERADMIN) {
            authorities.add(new SimpleGrantedAuthority("ALL_PRIVILEGES"));
        } else {
            userRepository.findByAuthId(auth.getAuthId())
                    .map(User::getUserId)
                    .ifPresent(userId -> userRoleRepository.findByUserId(userId)
                            .forEach(userRole -> rolePermissionRepository.findByRoleId(userRole.getRoleId())
                                    .stream()
                                    .map(RolePermission::getAuthorityCode)
                                    .distinct()
                                    .map(SimpleGrantedAuthority::new)
                                    .forEach(authorities::add)));
        }

        return new org.springframework.security.core.userdetails.User(
                auth.getUsername(),
                auth.getPasswordHash(),
                auth.getStatus() == AuthorizationStatus.ACTIVE,
                true,
                true,
                true,
                authorities
        );
    }
}
