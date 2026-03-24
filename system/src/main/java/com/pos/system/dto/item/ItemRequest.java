package com.pos.system.dto.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ItemRequest {

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotBlank(message = "SKU is required")
    @Size(max = 100, message = "SKU must not exceed 100 characters")
    private String sku;

    @NotBlank(message = "Item name is required")
    @Size(max = 200, message = "Item name must not exceed 200 characters")
    private String name;

    private String image;

    private Long categoryId;

    private Long brandId;

    private Boolean isWeighed = false;

    private String scaleBarcodePrefix;

    private Boolean isActive = true;
}
