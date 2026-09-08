package com.pos.system.dto.supplier;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PurchaseOrderSearchRequestDto {
    private String q;
    private String status;
    private String receivingStatus;
    private String paymentStatus;
    private Long supplierId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
