package com.pos.system.dto.supplier;

import lombok.Data;

@Data
public class SupplierSearchRequestDto {
    private String q;
    private Boolean active;
}
