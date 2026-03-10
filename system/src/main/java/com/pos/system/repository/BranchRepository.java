package com.pos.system.repository;

import com.pos.system.model.auth.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    boolean existsByName(String name);
}