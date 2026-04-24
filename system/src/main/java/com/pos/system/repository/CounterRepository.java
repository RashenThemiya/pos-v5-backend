package com.pos.system.repository;

import com.pos.system.model.cash.Counter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CounterRepository extends JpaRepository<Counter, Long> {
    List<Counter> findByBranchId(Long branchId);
    List<Counter> findByBranchIdAndIsActiveTrue(Long branchId);
    Optional<Counter> findByBranchIdAndName(Long branchId, String name);
}
