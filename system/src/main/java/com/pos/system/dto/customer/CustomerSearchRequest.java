package com.pos.system.dto.customer;

import lombok.Data;

@Data
public class CustomerSearchRequest {
    private String q;
    private Long branchId;
}
