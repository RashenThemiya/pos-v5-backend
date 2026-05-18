package com.pos.system.dto.scale;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ScaleBarcodeSettingRequest {

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotBlank(message = "Prefix is required")
    @Size(max = 10, message = "Prefix must not exceed 10 characters")
    private String prefix;

    @NotNull(message = "Total length is required")
    @Min(value = 1, message = "Total length must be greater than 0")
    private Integer totalLength;

    @NotNull(message = "Item code start is required")
    @Min(value = 1, message = "Item code start must be greater than 0")
    private Integer itemCodeStart;

    @NotNull(message = "Item code length is required")
    @Min(value = 1, message = "Item code length must be greater than 0")
    private Integer itemCodeLength;

    @NotBlank(message = "Value type is required")
    private String valueType;

    @NotNull(message = "Value start is required")
    @Min(value = 1, message = "Value start must be greater than 0")
    private Integer valueStart;

    @NotNull(message = "Value length is required")
    @Min(value = 1, message = "Value length must be greater than 0")
    private Integer valueLength;

    @NotNull(message = "Value decimal places is required")
    @Min(value = 0, message = "Value decimal places must be >= 0")
    private Integer valueDecimalPlaces;

    private Boolean isActive = true;
}
