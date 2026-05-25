package com.pos.system.dto.item;

import lombok.Data;

@Data
public class ItemSearchRequest {
    private Long branchId;
    private String q;
}