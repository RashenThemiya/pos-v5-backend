package com.pos.system.dto.customer;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CustomerRequest {
    private Long branchId;
    private Long authId;
    private String name;
    private String nic;
    private String phone;
    private String email;
    private String address;
    private BigDecimal creditLimit;
    private Boolean isActive;
}
