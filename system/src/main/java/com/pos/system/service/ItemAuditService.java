package com.pos.system.service;

import com.pos.system.dto.item.ItemAuditLogResponse;
import com.pos.system.model.catalog.ItemAuditLog;
import com.pos.system.repository.ItemAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemAuditService {
    private final ItemAuditLogRepository repository;

    public void record(Long branchId, Long itemId, String action, String entityType,
                       Long entityId, String details) {
        ItemAuditLog log = new ItemAuditLog();
        log.setBranchId(branchId);
        log.setItemId(itemId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setActor(currentActor());
        log.setDetails(details);
        log.setCreatedAt(LocalDateTime.now());
        repository.save(log);
    }

    public List<ItemAuditLogResponse> getByItem(Long branchId, Long itemId) {
        return repository.findByBranchIdAndItemIdOrderByCreatedAtDesc(branchId, itemId)
                .stream().map(this::map).toList();
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                ? authentication.getName() : "system";
    }

    private ItemAuditLogResponse map(ItemAuditLog log) {
        return ItemAuditLogResponse.builder()
                .auditLogId(log.getAuditLogId()).branchId(log.getBranchId()).itemId(log.getItemId())
                .action(log.getAction()).entityType(log.getEntityType()).entityId(log.getEntityId())
                .actor(log.getActor()).details(log.getDetails()).createdAt(log.getCreatedAt()).build();
    }
}
