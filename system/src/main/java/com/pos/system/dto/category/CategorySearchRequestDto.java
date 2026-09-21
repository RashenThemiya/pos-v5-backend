package com.pos.system.dto.category;

import lombok.Data;

@Data
public class CategorySearchRequestDto {
    private String q;
    private Boolean active;
}
