package com.pos.system.repository;

import com.pos.system.model.catalog.ScaleBarcodeSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScaleBarcodeSettingRepository extends JpaRepository<ScaleBarcodeSetting, Long> {
    Optional<ScaleBarcodeSetting> findByBranchIdAndPrefixAndIsActiveTrue(Long branchId, String prefix);
}