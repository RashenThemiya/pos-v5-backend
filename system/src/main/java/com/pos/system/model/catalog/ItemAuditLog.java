package com.pos.system.model.catalog;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "item_audit_logs", indexes = {
        @Index(name = "idx_item_audit_branch_item", columnList = "branch_id,item_id"),
        @Index(name = "idx_item_audit_created", columnList = "created_at")
})
public class ItemAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditLogId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(nullable = false, length = 40)
    private String entityType;

    private Long entityId;

    @Column(length = 150)
    private String actor;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
