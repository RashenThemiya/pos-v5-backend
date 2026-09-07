package com.pos.system.controller;

import com.pos.system.dto.item.ItemAuditLogResponse;
import com.pos.system.service.ItemAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@CrossOrigin
public class ItemAuditLogController {
    private final ItemAuditService itemAuditService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ITEM_VIEW')")
    @GetMapping("/branch/{branchId}/item/{itemId}")
    public ResponseEntity<List<ItemAuditLogResponse>> getItemAuditLogs(
            @PathVariable Long branchId, @PathVariable Long itemId) {
        return ResponseEntity.ok(itemAuditService.getByItem(branchId, itemId));
    }
}
