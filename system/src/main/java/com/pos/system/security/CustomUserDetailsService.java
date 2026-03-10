package com.pos.system.security;

import com.pos.system.model.auth.Authorization;
import com.pos.system.model.auth.AuthorizationStatus;
import com.pos.system.model.auth.AuthorizationType;
import com.pos.system.repository.AuthorizationRepository;
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

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Authorization auth = authorizationRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + auth.getType().name()));

        if (auth.getType() == AuthorizationType.SUPERADMIN) {
            authorities.add(new SimpleGrantedAuthority("ALL_PRIVILEGES"));
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