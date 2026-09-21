package com.pos.system.dto.supplier;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PurchaseReturnSearchRequestDto {
    private String q;
    private String status;
    private Long supplierId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
