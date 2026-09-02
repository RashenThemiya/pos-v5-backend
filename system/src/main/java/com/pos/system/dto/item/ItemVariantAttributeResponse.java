package com.pos.system.dto.item;

import lombok.Data;

@Data
public class ItemVariantAttributeResponse {
    private Long attributeId;
    private String attributeName;
    private String attributeValue;
}
