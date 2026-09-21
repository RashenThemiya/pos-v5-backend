package com.pos.system.dto.item;

import lombok.Data;

@Data
public class ItemSearchRequest {
    private Long branchId;
    private Long categoryId;
    private Long brandId;
    private Boolean active;
    private String q;
}
