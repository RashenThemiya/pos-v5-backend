package com.pos.system.dto.customer;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CustomerResponse {
    private Long customerId;
    private Long branchId;
    private Long authId;
    private String name;
    private String nic;
    private String phone;
    private String email;
    private String address;
    private BigDecimal shopBalance;
    private String loyaltyCardNo;
    private BigDecimal loyaltyPoints;
    private BigDecimal creditLimit;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
