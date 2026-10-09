package com.pos.system.repository;

import com.pos.system.model.cash.CounterVarianceAlertRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CounterVarianceAlertReadRepository extends JpaRepository<CounterVarianceAlertRead, Long> {
    List<CounterVarianceAlertRead> findByBranchIdAndAlertIdIn(Long branchId, Collection<String> alertIds);

    boolean existsByBranchIdAndAlertId(Long branchId, String alertId);
}
