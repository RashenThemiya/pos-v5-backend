package com.pos.system.repository;

import com.pos.system.model.catalog.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {
    List<TaxRate> findByBranchId(Long branchId);
    List<TaxRate> findByBranchIdAndIsActive(Long branchId, Boolean isActive);
    boolean existsByBranchIdAndName(Long branchId, String name);
    Optional<TaxRate> findByTaxIdAndBranchId(Long taxId, Long branchId);
}
