package com.pos.system.dto.taxrate;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ItemTaxRequest {

    @NotNull(message = "Tax ID is required")
    private Long taxId;
}
