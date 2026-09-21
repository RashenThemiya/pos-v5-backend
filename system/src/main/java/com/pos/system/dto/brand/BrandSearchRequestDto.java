package com.pos.system.dto.brand;

import lombok.Data;

@Data
public class BrandSearchRequestDto {
    private String q;
    private Boolean active;
}
