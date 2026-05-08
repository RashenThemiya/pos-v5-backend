package com.pos.system.dto.branch;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BranchResponse {
    private Long branchId;
    private String name;
    private String address;
    private String phone;
    private String timezone;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
