package com.pos.system.repository;

import com.pos.system.model.auth.Authorization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorizationRepository extends JpaRepository<Authorization, Long> {
    Optional<Authorization> findByUsername(String username);
    Optional<Authorization> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}