package com.pos.system.repository;

import com.pos.system.model.catalog.ItemAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemAuditLogRepository extends JpaRepository<ItemAuditLog, Long> {
    List<ItemAuditLog> findByBranchIdAndItemIdOrderByCreatedAtDesc(Long branchId, Long itemId);
}
