package com.pos.system.dto.scale;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScaleBarcodeSettingResponse {
    private Long settingId;
    private Long branchId;
    private String prefix;
    private Integer totalLength;
    private Integer itemCodeStart;
    private Integer itemCodeLength;
    private String valueType;
    private Integer valueStart;
    private Integer valueLength;
    private Integer valueDecimalPlaces;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
