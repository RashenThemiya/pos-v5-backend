package com.pos.system.dto.stock;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OpeningStockItemRequestDto {
    private Long itemId;
    private Long variantId;
    private Long unitId;
    private BigDecimal quantity;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private String batchNo;
    private String supplierBatchBarcode;
    private LocalDate expiryDate;
    private String note;
}
