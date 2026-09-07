package com.pos.system.dto.item;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ItemAuditLogResponse {
    private Long auditLogId;
    private Long branchId;
    private Long itemId;
    private String action;
    private String entityType;
    private Long entityId;
    private String actor;
    private String details;
    private LocalDateTime createdAt;
}
