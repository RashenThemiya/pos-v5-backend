package com.pos.system.dto.supplier;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SupplySearchRequestDto {
    private String q;
    private String status;
    private String paymentStatus;
    private Long supplierId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
