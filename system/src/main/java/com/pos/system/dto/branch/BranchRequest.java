package com.pos.system.dto.branch;

import lombok.Data;

@Data
public class BranchRequest {
    private String name;
    private String address;
    private String phone;
    private String timezone;
    private Boolean isActive;
}