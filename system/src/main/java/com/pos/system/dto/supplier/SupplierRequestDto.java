package com.pos.system.dto.supplier;

import lombok.Data;

@Data
public class SupplierRequestDto {
    private Long branchId;
    private Long authId;
    private String name;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private Boolean isActive;
}