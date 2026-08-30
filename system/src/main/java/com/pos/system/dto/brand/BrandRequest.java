package com.pos.system.dto.brand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class BrandRequest {

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotBlank(message = "Brand name is required")
    @Size(max = 100, message = "Brand name must not exceed 100 characters")
    private String name;

    private Boolean isActive = true;

    private Set<Long> categoryIds;
}
