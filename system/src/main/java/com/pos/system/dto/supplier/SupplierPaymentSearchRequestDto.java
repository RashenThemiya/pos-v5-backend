package com.pos.system.dto.supplier;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SupplierPaymentSearchRequestDto {
    private String q;
    private String paymentMethod;
    private Long supplierId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
