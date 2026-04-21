package com.pos.system.service;

import com.pos.system.dto.supplier.*;

import java.util.List;

public interface SupplierManagementService {

    SupplierResponseDto createSupplier(SupplierRequestDto dto);
    SupplierResponseDto updateSupplier(Long supplierId, SupplierRequestDto dto);
    SupplierResponseDto getSupplierById(Long supplierId);
    List<SupplierResponseDto> getSuppliersByBranch(Long branchId);

    PurchaseOrderResponseDto createPurchaseOrder(PurchaseOrderRequestDto dto);
    PurchaseOrderResponseDto getPurchaseOrderById(Long poId);
    List<PurchaseOrderResponseDto> getPurchaseOrdersByBranch(Long branchId);

    SupplyResponseDto createSupply(SupplyRequestDto dto);
    SupplyResponseDto getSupplyById(Long supplyId);
    List<SupplyResponseDto> getSuppliesByBranch(Long branchId);

    SupplierPaymentResponseDto createSupplierPayment(SupplierPaymentRequestDto dto);
}