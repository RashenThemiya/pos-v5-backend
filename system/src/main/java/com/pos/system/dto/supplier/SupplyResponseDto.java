package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SupplyResponseDto {
    private Long supplyId;
    private Long branchId;
    private Long supplierId;
    private Long poId;
    private String grnNo;
    private String invoiceNo;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal taxAmount;
    private BigDecimal rounding;
    private BigDecimal total;
    private BigDecimal paidAmount;
    private String paymentMethod;
    private String status;
    private LocalDateTime supplyDate;
    private Long receivedBy;
    private String notes;
    private List<SupplyProductResponseDto> products;
}