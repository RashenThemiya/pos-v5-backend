package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseOrderResponseDto {
    private Long poId;
    private Long branchId;
    private Long supplierId;
    private String poNo;
    private String status;
    private LocalDate expectedDate;
    private Long createdBy;
    private LocalDateTime createdAt;
    private String note;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private String receivingStatus;
    private String paymentStatus;
    private List<PurchaseOrderItemResponseDto> items;
}
