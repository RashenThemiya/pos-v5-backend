package com.pos.system.service;

import com.pos.system.dto.supplier.*;

import java.util.List;

public interface SupplierManagementService {

    // =========================
    // SUPPLIER
    // =========================

    SupplierResponseDto createSupplier(SupplierRequestDto dto);

    SupplierResponseDto updateSupplier(Long supplierId, SupplierRequestDto dto);

    SupplierResponseDto getSupplierById(Long supplierId);

    List<SupplierResponseDto> getSuppliersByBranch(Long branchId);

    // =========================
    // SUPPLIER ITEMS
    // =========================

    SupplierItemResponseDto addSupplierItem(SupplierItemRequestDto dto);

    SupplierItemResponseDto updateSupplierItem(Long supplierItemId, SupplierItemRequestDto dto);

    List<SupplierItemResponseDto> getSupplierItemsBySupplier(Long branchId, Long supplierId);

    List<SupplierItemResponseDto> getSuppliersByItem(Long branchId, Long itemId);

    void deleteSupplierItem(Long supplierItemId);

    // =========================
    // PURCHASE ORDER
    // =========================

    PurchaseOrderResponseDto createPurchaseOrder(PurchaseOrderRequestDto dto);

    PurchaseOrderResponseDto getPurchaseOrderById(Long poId);

    List<PurchaseOrderResponseDto> getPurchaseOrdersByBranch(Long branchId);

    // =========================
    // SUPPLY / GRN
    // =========================

    SupplyResponseDto createSupply(SupplyRequestDto dto);

    SupplyResponseDto getSupplyById(Long supplyId);

    List<SupplyResponseDto> getSuppliesByBranch(Long branchId);

    List<SupplyResponseDto> getUnpaidSuppliesByBranch(Long branchId);

    List<SupplyResponseDto> getUnpaidSuppliesBySupplier(Long branchId, Long supplierId);

    // =========================
    // SUPPLIER PAYMENTS
    // =========================

    SupplierPaymentResponseDto createSupplierPayment(SupplierPaymentRequestDto dto);

    SupplierPaymentResponseDto getSupplierPaymentById(Long paymentId);

    List<SupplierPaymentResponseDto> getPaymentsByBranch(Long branchId);

    List<SupplierPaymentResponseDto> getPaymentsBySupplier(Long branchId, Long supplierId);

    List<SupplierPaymentResponseDto> getPaymentsBySupply(Long supplyId);

    List<SupplierPaymentResponseDto> getPaymentsByPurchaseOrder(Long poId);

    // =========================
    // PURCHASE RETURNS
    // =========================

    PurchaseReturnResponseDto createPurchaseReturn(PurchaseReturnRequestDto dto);

    PurchaseReturnResponseDto getPurchaseReturnById(Long purchaseReturnId);

    List<PurchaseReturnResponseDto> getPurchaseReturnsByBranch(Long branchId);

    List<PurchaseReturnResponseDto> getPurchaseReturnsBySupplier(Long branchId, Long supplierId);

    List<PurchaseReturnResponseDto> getPurchaseReturnsBySupply(Long supplyId);

    // =========================
    // SUPPLIER LEDGER
    // =========================

    List<SupplierBalanceTransactionResponseDto> getSupplierLedger(Long branchId, Long supplierId);
}
