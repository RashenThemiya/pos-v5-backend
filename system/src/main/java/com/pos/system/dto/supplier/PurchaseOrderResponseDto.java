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
    private String supplierName;
    private String supplierContactPerson;
    private String supplierPhone;
    private String supplierEmail;
    private String supplierAddress;
    private String poNo;
    private String status;
    private LocalDate orderDate;
    private LocalDate expectedDate;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private String note;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private String receivingStatus;
    private String paymentStatus;
    private Integer itemCount;
    private BigDecimal orderedQuantity;
    private BigDecimal receivedQuantity;
    private BigDecimal remainingQuantity;
    private Boolean overdue;
    private List<PurchaseOrderItemResponseDto> items;
    private List<SupplyResponseDto> receipts;
    private List<SupplierPaymentResponseDto> payments;
    private List<PurchaseReturnResponseDto> purchaseReturns;
}
