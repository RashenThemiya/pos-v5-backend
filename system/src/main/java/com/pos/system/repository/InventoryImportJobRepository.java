package com.pos.system.repository;

import com.pos.system.model.stock.InventoryImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface InventoryImportJobRepository extends JpaRepository<InventoryImportJob, Long> {
    Optional<InventoryImportJob> findByBranchIdAndFileHash(Long branchId, String fileHash);
    List<InventoryImportJob> findTop100ByBranchIdOrderByCreatedAtDesc(Long branchId);
    List<InventoryImportJob> findByBranchIdAndModeAndStatus(Long branchId, String mode, String status);
}
