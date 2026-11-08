package com.pos.system.service;

import com.pos.system.dto.sale.*;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.ItemVariant;
import com.pos.system.model.catalog.ScaleBarcodeSetting;
import com.pos.system.model.catalog.ScaleItemMapping;
import com.pos.system.model.catalog.UnitMaster;
import com.pos.system.model.cash.CashSessionTransaction;
import com.pos.system.model.promotion.Promotion;
import com.pos.system.model.promotion.PromotionBatch;
import com.pos.system.model.promotion.PromotionItem;
import com.pos.system.model.promotion.OrderProductPromotion;
import com.pos.system.model.sale.*;
import com.pos.system.model.stock.Stock;
import com.pos.system.model.stock.StockBatch;
import com.pos.system.model.stock.StockMovement;
import com.pos.system.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SalesServiceImpl implements SalesService {

    private static final String RETURN_VOUCHER_PAYMENT_METHOD = "RETURN_VOUCHER";
    private static final String BALANCE_ADJUSTMENT_REFUND_METHOD = "BALANCE_ADJUSTMENT";

    private final CustomerOrderRepository orderRepository;
    private final OrderProductRepository orderProductRepository;
    private final PaymentRepository paymentRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final SalesReturnRepository returnRepository;
    private final SalesReturnItemRepository returnItemRepository;
    private final StockRepository stockRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CashSessionTransactionRepository cashSessionTransactionRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final UnitMasterRepository unitMasterRepository;
    private final ItemRepository itemRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final ItemVariantAttributeRepository itemVariantAttributeRepository;
    private final ScaleBarcodeSettingRepository scaleBarcodeSettingRepository;
    private final ScaleItemMappingRepository scaleItemMappingRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionItemRepository promotionItemRepository;
    private final PromotionBatchRepository promotionBatchRepository;
    private final CustomerService customerService;
    private final SalePromotionService salePromotionService;

    // ─── Orders ──────────────────────────────────────────────────────────────────

    @Override
    public OrderResponse processSale(ProcessSaleRequest request) {
        validateProcessSaleReturnVoucherPayments(request.getPayments());

        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setBranchId(request.getBranchId());
        orderRequest.setUserId(request.getUserId());
        orderRequest.setCustomerId(request.getCustomerId());
        orderRequest.setCashSessionId(request.getCashSessionId());
        orderRequest.setItems(request.getItems());
        orderRequest.setBillPromotionIds(request.getBillPromotionIds());
        orderRequest.setDiscount(request.getDiscount());
        orderRequest.setRounding(request.getRounding());
        orderRequest.setNotes(request.getNotes());

        OrderResponse order = createOrder(orderRequest);

        if (request.getPayments() == null || request.getPayments().isEmpty()) {
            return order;
        }

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setReceivedBy(request.getUserId());
        paymentRequest.setPayments(request.getPayments().stream()
                .map(this::mapProcessSalePaymentLine)
                .toList());

        return processPayment(order.getOrderId(), paymentRequest);
    }

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Order must have at least one item");
        }

        CustomerOrder order = new CustomerOrder();
        order.setBranchId(request.getBranchId());
        order.setInvoiceNo(generateInvoiceNo(request.getBranchId()));
        order.setOrderNo(generateOrderNo(request.getBranchId()));
        order.setUserId(request.getUserId());
        order.setCustomerId(request.getCustomerId());
        order.setCashSessionId(request.getCashSessionId());
        order.setDiscount(nvl(request.getDiscount()));
        order.setRounding(nvl(request.getRounding()));
        order.setNotes(request.getNotes());
        order.setStatus("COMPLETED");
        order.setPaymentStatus("UNPAID");
        order.setOrderDate(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setTaxAmount(BigDecimal.ZERO);

        CustomerOrder savedOrder = orderRepository.save(order);

        // save items and deduct stock
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderProductRequest item : request.getItems()) {
            item.setVariantId(resolveOrderLineVariantId(request.getBranchId(), item));
            BigDecimal lineDiscount = nvl(item.getDiscount());
            if (lineDiscount.signum() < 0 || lineDiscount.compareTo(item.getUnitPrice().multiply(item.getQuantity())) > 0) {
                throw new IllegalArgumentException("Line discount must be between zero and the line subtotal");
            }
            BigDecimal lineTotal = item.getUnitPrice()
                    .multiply(item.getQuantity())
                    .subtract(lineDiscount)
                    .setScale(2, RoundingMode.HALF_UP);

            OrderProduct op = new OrderProduct();
            op.setOrderId(savedOrder.getOrderId());
            op.setItemId(item.getItemId());
            op.setVariantId(item.getVariantId());
            op.setUnitId(item.getUnitId());
            op.setBatchBarcode(item.getBatchBarcode());
            op.setQuantity(item.getQuantity());
            op.setUnitPrice(item.getUnitPrice());
            op.setDiscount(lineDiscount);
            op.setLineTotal(lineTotal);
            op.setCreatedAt(LocalDateTime.now());
            orderProductRepository.save(op);
            salePromotionService.recordLine(savedOrder, op, item.getPromotionId());

            subtotal = subtotal.add(lineTotal);

            // deduct stock
            deductStock(request.getBranchId(), item, savedOrder.getOrderId(), request.getUserId());
        }

        // update order totals
        BigDecimal total = subtotal
                .subtract(nvl(request.getDiscount()))
                .add(nvl(request.getRounding()))
                .setScale(2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);

        savedOrder.setSubtotal(subtotal);
        savedOrder.setTotal(total);
        List<Long> billPromotionIds = request.getBillPromotionIds();
        if ((billPromotionIds == null || billPromotionIds.isEmpty()) && request.getPromotionId() != null) {
            billPromotionIds = List.of(request.getPromotionId());
        }
        if (savedOrder.getDiscount().signum() < 0 || savedOrder.getDiscount().compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Bill discount must be between zero and the subtotal");
        }
        salePromotionService.recordBill(savedOrder, billPromotionIds);
        orderRepository.save(savedOrder);

        recordStatusHistory(savedOrder.getOrderId(), null, "COMPLETED", request.getUserId(), "Order created");

        return buildOrderResponse(savedOrder);
    }

    @Override
    public OrderResponse getOrderById(Long orderId) {
        return buildOrderResponse(findOrderById(orderId));
    }

    @Override
    public OrderResponse getOrderByInvoiceNo(String invoiceNo) {
        CustomerOrder order = orderRepository.findByInvoiceNo(invoiceNo)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceNo));
        return buildOrderResponse(order);
    }

    @Override
    public List<OrderResponse> getOrdersByBranch(Long branchId) {
        return orderRepository.findByBranchIdOrderByOrderDateDesc(branchId)
                .stream().map(this::buildOrderResponse).toList();
    }

    @Override
    public CustomerOrderPageResponse getOrdersByBranch(Long branchId, Pageable pageable) {
        Page<CustomerOrder> orders = orderRepository.findByBranchId(branchId, pageable);

        return CustomerOrderPageResponse.builder()
                .content(orders.getContent().stream().map(this::buildCustomerOrderViewResponse).toList())
                .page(orders.getNumber())
                .pageSize(orders.getSize())
                .totalElements(orders.getTotalElements())
                .totalPages(orders.getTotalPages())
                .sort(formatPageSort(pageable))
                .build();
    }

    @Override
    public CustomerOrderPageResponse getCustomerOrdersByBranch(Long branchId, Long customerId, Pageable pageable) {
        Page<CustomerOrder> orders = orderRepository.findByBranchIdAndCustomerId(branchId, customerId, pageable);

        return CustomerOrderPageResponse.builder()
                .content(orders.getContent().stream().map(this::buildCustomerOrderViewResponse).toList())
                .page(orders.getNumber())
                .pageSize(orders.getSize())
                .totalElements(orders.getTotalElements())
                .totalPages(orders.getTotalPages())
                .sort(formatPageSort(pageable))
                .build();
    }

    @Override
    public List<OrderResponse> getOrdersByItem(Long branchId, Long itemId) {
        return orderRepository.findByBranchIdAndItemId(branchId, itemId)
                .stream().map(this::buildOrderResponse).toList();
    }

    @Override
    public List<OrderResponse> getOrdersBySession(Long branchId, Long sessionId) {
        return orderRepository.findByBranchIdAndCashSessionIdOrderByOrderDateDesc(branchId, sessionId)
                .stream().map(this::buildOrderResponse).toList();
    }

    @Override
    public List<OrderResponse> getOrdersByDateRange(Long branchId, LocalDateTime from, LocalDateTime to) {
        return orderRepository.findByBranchIdAndOrderDateBetweenOrderByOrderDateDesc(branchId, from, to)
                .stream().map(this::buildOrderResponse).toList();
    }

    @Override
    public OrderResponse cancelOrder(Long orderId, CancelOrderRequest request) {
        CustomerOrder order = findOrderById(orderId);

        if ("CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("Order is already cancelled");
        }

        // reverse stock for each item
        List<OrderProduct> products = orderProductRepository.findByOrderId(orderId);
        for (OrderProduct op : products) {
            reverseStock(order.getBranchId(), op, orderId, request.getCancelledBy());
        }

        String oldStatus = order.getStatus();
        order.setStatus("CANCELLED");
        order.setCancelledBy(request.getCancelledBy());
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelReason(request.getCancelReason());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        recordStatusHistory(orderId, oldStatus, "CANCELLED", request.getCancelledBy(), request.getCancelReason());

        return buildOrderResponse(order);
    }

    // ─── Payments ────────────────────────────────────────────────────────────────

    @Override
    public List<SaleProductSearchResponse> searchProductsForSale(Long branchId, String query) {
        String normalized = normalizeSearch(query);
        if (normalized == null) {
            throw new RuntimeException("Search query is required");
        }

        Map<String, SaleProductSearchResponse> results = new LinkedHashMap<>();

        itemUnitRepository.findByBranchIdAndBarcodeAndIsActiveTrue(branchId, normalized)
                .ifPresent(unit -> addResult(results, buildSaleSearchResult(
                        "ITEM_UNIT_BARCODE",
                        findActiveItem(branchId, unit.getItemId()),
                        null,
                        unit,
                        null,
                        null
                )));

        itemVariantRepository.findByBranchIdAndSku(branchId, normalized)
                .ifPresent(variant -> addResult(results, buildSaleSearchResult(
                        "VARIANT_SKU",
                        findActiveItem(branchId, variant.getItemId()),
                        variant,
                        findBaseUnit(variant.getItemId()).orElse(null),
                        null,
                        null
                )));

        decodeScaleBarcode(branchId, normalized).ifPresent(result -> addResult(results, result));

        stockBatchRepository.searchByBranchAndBatchText(branchId, normalized)
                .forEach(batch -> addResult(results, buildSaleSearchResult(
                        "STOCK_BATCH",
                        findActiveItem(branchId, batch.getItemId()),
                        resolveVariant(batch.getVariantId()).orElse(null),
                        findUnit(batch.getItemId(), batch.getUnitId()).orElse(null),
                        batch,
                        null
                )));

        itemRepository.searchActiveByBranchAndNameOrSku(branchId, normalized)
                .forEach(item -> addResult(results, buildSaleSearchResult(
                        "ITEM",
                        item,
                        null,
                        findBaseUnit(item.getItemId()).orElse(null),
                        null,
                        null
                )));

        return new ArrayList<>(results.values());
    }

    @Override
    public OrderResponse processPayment(Long orderId, PaymentRequest request) {
        CustomerOrder order = findOrderById(orderId);

        if ("CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("Cannot pay for a cancelled order");
        }
        if ("PAID".equals(order.getPaymentStatus())) {
            throw new RuntimeException("Order is already fully paid");
        }
        if (request.getPayments() == null || request.getPayments().isEmpty()) {
            throw new RuntimeException("At least one payment line is required");
        }
        validatePaymentRequestReturnVoucherPayments(request.getPayments());

        // Goods returned while unpaid are no longer owed.
        BigDecimal orderTotal = nvl(order.getTotal()).subtract(returnBalanceAdjustment(orderId)).max(BigDecimal.ZERO);
        BigDecimal runningPaid = paymentRepository.findByOrderId(orderId).stream()
                .map(payment -> nvl(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (runningPaid.compareTo(orderTotal) >= 0) {
            throw new RuntimeException("Order is already fully paid");
        }

        for (PaymentRequest.PaymentLineDto line : request.getPayments()) {
            BigDecimal remainingDue = orderTotal.subtract(runningPaid).max(BigDecimal.ZERO);
            BigDecimal appliedAmount = resolveAppliedPaymentAmount(line, remainingDue);
            BigDecimal tenderedAmount = resolveTenderedAmount(line, appliedAmount);

            Payment payment = new Payment();
            payment.setBranchId(order.getBranchId());
            payment.setOrderId(orderId);
            payment.setCustomerId(order.getCustomerId());
            payment.setCashSessionId(order.getCashSessionId());
            payment.setAmount(appliedAmount);
            payment.setTenderedAmount(tenderedAmount);
            payment.setChangeAmount(resolveChangeAmount(line, tenderedAmount, appliedAmount));
            payment.setPaymentMethod(line.getPaymentMethod());
            payment.setPaymentDate(LocalDateTime.now());
            payment.setReceivedBy(request.getReceivedBy());
            payment.setReferenceNo(line.getReferenceNo());
            payment.setNote(line.getNote());
            Payment savedPayment = paymentRepository.save(payment);

            runningPaid = runningPaid.add(appliedAmount);

            if ("CREDIT".equalsIgnoreCase(line.getPaymentMethod())) {
                if (order.getCustomerId() == null) {
                    throw new RuntimeException("Customer is required for credit payments");
                }
                customerService.recordCreditSale(
                        order.getCustomerId(),
                        appliedAmount,
                        order.getOrderId(),
                        order.getInvoiceNo(),
                        request.getReceivedBy()
                );
            }

            // write cash session transaction
            if (order.getCashSessionId() != null) {
                CashSessionTransaction txn = new CashSessionTransaction();
                txn.setSessionId(order.getCashSessionId());
                txn.setType("SALE");
                txn.setAmount(appliedAmount);
                txn.setPaymentMethod(line.getPaymentMethod());
                txn.setPaymentId(savedPayment.getPaymentId());
                txn.setOrderId(order.getOrderId());
                txn.setInvoiceNo(order.getInvoiceNo());
                txn.setNote("Invoice: " + order.getInvoiceNo());
                txn.setCreatedBy(request.getReceivedBy());
                txn.setCreatedAt(LocalDateTime.now());
                cashSessionTransactionRepository.save(txn);
            }
        }

        // update payment status
        if (runningPaid.compareTo(orderTotal) >= 0) {
            order.setPaymentStatus("PAID");
        } else {
            order.setPaymentStatus("PARTIAL");
        }
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        return buildOrderResponse(order);
    }

    @Override
    public List<PaymentResponse> getPaymentsByOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .stream().map(this::mapPayment).toList();
    }

    // ─── Returns ─────────────────────────────────────────────────────────────────

    @Override
    public SalesReturnResponse createReturn(SalesReturnRequest request) {
        if (request.getOrderId() == null) {
            throw new RuntimeException("A return must reference the original order");
        }
        // Lock the order row so concurrent returns against it are serialised. The ledger below
        // then sees every committed prior return.
        CustomerOrder order = orderRepository.findByIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + request.getOrderId()));

        if ("CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("Cannot return items from a cancelled order");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Return must have at least one item");
        }

        // Validate and price the whole request before writing returns, stock or cash.
        SalesReturnLedger ledger = buildReturnLedger(order);
        List<PlannedReturnLine> plan = new ArrayList<>();
        for (SalesReturnRequest.ReturnItemDto item : request.getItems()) {
            if (item.getItemId() == null) {
                throw new RuntimeException("Return line is missing itemId");
            }
            item.setVariantId(resolveReturnLineVariantId(request.getBranchId(), item));
            for (SalesReturnLedger.Allocation allocation : ledger.reserve(item.getOrderProductId(), item.getItemId(),
                    item.getVariantId(), item.getInternalBatchBarcode(), item.getQuantity())) {
                plan.add(new PlannedReturnLine(item, allocation));
            }
        }
        ReturnSettlement settlement = settleReturn(order, plan, request.getRefundMethod());
        Long accountCustomerId = request.getCustomerId() != null ? request.getCustomerId() : order.getCustomerId();
        if (settlement.accountCredit().signum() > 0 && accountCustomerId == null) {
            throw new RuntimeException("A customer is required to credit this return to an account");
        }

        BigDecimal totalRefund = BigDecimal.ZERO;

        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setBranchId(request.getBranchId());
        salesReturn.setOrderId(request.getOrderId());
        salesReturn.setCustomerId(request.getCustomerId());
        salesReturn.setReturnDate(LocalDateTime.now());
        salesReturn.setRefundMethod(request.getRefundMethod());
        salesReturn.setReason(request.getReason());
        salesReturn.setProcessedBy(request.getProcessedBy());
        salesReturn.setStatus("COMPLETED");

        SalesReturn savedReturn = returnRepository.save(salesReturn);

        for (PlannedReturnLine planned : plan) {
            OrderProduct soldLine = planned.allocation().product();
            BigDecimal lineRefund = planned.allocation().refund();
            totalRefund = totalRefund.add(lineRefund);

            // Server-side view of the line: quantity, batch and refund price come from the original sale.
            SalesReturnRequest.ReturnItemDto line = new SalesReturnRequest.ReturnItemDto();
            line.setOrderProductId(soldLine.getOrderProductId());
            line.setItemId(soldLine.getItemId());
            line.setVariantId(soldLine.getVariantId());
            String requestedBatch = planned.request().getInternalBatchBarcode();
            line.setInternalBatchBarcode(requestedBatch != null && !requestedBatch.isBlank()
                    ? requestedBatch : soldLine.getBatchBarcode());
            line.setQuantity(planned.allocation().quantity());
            line.setUnitPrice(planned.allocation().unitRefund());
            line.setCondition(planned.request().getCondition());

            SalesReturnItem returnItem = new SalesReturnItem();
            returnItem.setReturnId(savedReturn.getReturnId());
            returnItem.setOrderProductId(line.getOrderProductId());
            returnItem.setItemId(line.getItemId());
            returnItem.setVariantId(line.getVariantId());
            returnItem.setInternalBatchBarcode(line.getInternalBatchBarcode());
            returnItem.setQuantity(line.getQuantity());
            returnItem.setUnitPrice(line.getUnitPrice());
            returnItem.setLineRefund(lineRefund);
            returnItem.setCondition(line.getCondition());
            returnItemRepository.save(returnItem);

            // add stock back, in base units: the line quantity is in the unit it was sold in
            BigDecimal baseQty = line.getQuantity().multiply(baseUnitMultiplier(soldLine));
            addReturnStock(request.getBranchId(), line, baseQty, soldLine.getUnitId(),
                    savedReturn.getReturnId(), request.getProcessedBy());
        }
        // Line refunds are the goods' value; the settlement decides how much is paid back vs. written off.
        savedReturn.setReturnValue(totalRefund.setScale(2, RoundingMode.HALF_UP));
        savedReturn.setBalanceAdjustment(settlement.balanceAdjustment());
        savedReturn.setRefundAmount(settlement.refund());
        savedReturn.setAccountCredit(settlement.accountCredit());
        savedReturn.setCreditReversal(settlement.creditReversal());
        returnRepository.save(savedReturn);
        totalRefund = settlement.refund();

        if (settlement.accountCredit().signum() > 0) {
            customerService.recordReturnCredit(accountCustomerId, settlement.accountCredit(),
                    savedReturn.getReturnId(), order.getInvoiceNo(), request.getProcessedBy());
        }

        if (settlement.balanceAdjustment().signum() > 0) {
            refreshPaymentStatus(order, settlement);
        }

        // write cash session transaction for cash refund
        if ("CASH".equals(request.getRefundMethod()) && order.getCashSessionId() != null
                && totalRefund.signum() > 0) {
            CashSessionTransaction txn = new CashSessionTransaction();
            txn.setSessionId(order.getCashSessionId());
            txn.setType("REFUND");
            txn.setAmount(totalRefund);
            txn.setPaymentMethod("CASH");
            txn.setOrderId(order.getOrderId());
            txn.setInvoiceNo(order.getInvoiceNo());
            txn.setSalesReturnId(savedReturn.getReturnId());
            txn.setNote("Return for invoice: " + order.getInvoiceNo());
            txn.setCreatedBy(request.getProcessedBy());
            txn.setCreatedAt(LocalDateTime.now());
            cashSessionTransactionRepository.save(txn);
        }

        return buildReturnResponse(savedReturn);
    }

    @Override
    public SalesReturnResponse getReturnById(Long returnId) {
        return buildReturnResponse(findReturnById(returnId));
    }

    @Override
    public ReturnVoucherResponse getReturnVoucher(String voucherNoOrCode) {
        Long returnId = resolveReturnVoucherId(voucherNoOrCode)
                .orElseThrow(() -> new RuntimeException("Return voucher not found: " + voucherNoOrCode));
        SalesReturn salesReturn = findReturnById(returnId);
        if (!RETURN_VOUCHER_PAYMENT_METHOD.equalsIgnoreCase(salesReturn.getRefundMethod())) {
            throw new RuntimeException("This return was not issued as a return voucher");
        }
        return buildReturnVoucherResponse(salesReturn);
    }

    @Override
    public List<SalesReturnResponse> getReturnsByOrder(Long orderId) {
        return returnRepository.findByOrderId(orderId)
                .stream().map(this::buildReturnResponse).toList();
    }

    @Override
    public List<SalesReturnResponse> getReturnsByBranch(Long branchId) {
        return returnRepository.findByBranchIdOrderByReturnDateDesc(branchId)
                .stream().map(this::buildReturnResponse).toList();
    }

    @Override
    public Page<SalesReturnResponse> getReturnsByBranch(Long branchId, Pageable pageable) {
        return returnRepository.findByBranchId(branchId, pageable)
                .map(this::buildReturnResponse);
    }

    // ─── Stock deduction ─────────────────────────────────────────────────────────

    private void deductStock(Long branchId, OrderProductRequest item, Long orderId, Long userId) {
        // convert qty to base units
        BigDecimal multiplier = itemUnitRepository
                .findByItemIdAndUnitIdAndIsActiveTrue(item.getItemId(), item.getUnitId())
                .orElseThrow(() -> new RuntimeException("Unit not found for item: " + item.getItemId()))
                .getMultiplierToBase();

        BigDecimal baseQty = item.getQuantity().multiply(multiplier);

        // update stock total
        Stock stock = findStock(branchId, item.getItemId(), item.getVariantId())
                .orElseThrow(() -> new RuntimeException("No stock record for item: " + item.getItemId()));

        BigDecimal availableQty = getBatchQtyTotal(branchId, item.getItemId(), item.getVariantId(), StockQtyType.AVAILABLE);
        if (availableQty.compareTo(baseQty) < 0) {
            throw new RuntimeException("Insufficient stock for item: " + item.getItemId()
                    + " (available: " + availableQty + ", required: " + baseQty + ")");
        }

        stock.setLastUpdated(LocalDateTime.now());
        stockRepository.save(stock);

        // deduct from specific batch if given, otherwise from oldest batch (FIFO)
        if (item.getBatchBarcode() != null && !item.getBatchBarcode().isBlank()) {
            StockBatch batch = stockBatchRepository
                    .findByBranchIdAndInternalBatchBarcode(branchId, item.getBatchBarcode())
                    .orElseThrow(() -> new RuntimeException("Batch not found: " + item.getBatchBarcode()));
            if (!batch.getItemId().equals(item.getItemId())) {
                throw new RuntimeException("Batch does not belong to selected item");
            }
            if (!sameVariant(batch.getVariantId(), item.getVariantId())) {
                throw new RuntimeException("Batch does not belong to selected variant");
            }
            BigDecimal batchRemainingQty = nvlQty(batch.getQtyRemaining());
            BigDecimal batchAvailableQty = nvlQty(batch.getAvailableQty());
            if (batchRemainingQty.compareTo(baseQty) < 0 || batchAvailableQty.compareTo(baseQty) < 0) {
                throw new RuntimeException("Insufficient stock in selected batch: " + item.getBatchBarcode()
                        + " (available: " + batchAvailableQty + ", required: " + baseQty + ")");
            }
            batch.setQtyRemaining(batchRemainingQty.subtract(baseQty));
            batch.setAvailableQty(batchAvailableQty.subtract(baseQty));
            stockBatchRepository.save(batch);
        } else {
            // FIFO — deduct from oldest batches first
            deductFIFO(branchId, item.getItemId(), item.getVariantId(), baseQty);
        }

        // record stock movement
        StockMovement movement = new StockMovement();
        movement.setBranchId(branchId);
        movement.setMovementType("SALE");
        movement.setItemId(item.getItemId());
        movement.setVariantId(item.getVariantId());
        movement.setUnitId(item.getUnitId());
        movement.setInternalBatchBarcode(item.getBatchBarcode());
        movement.setQuantity(baseQty.negate());
        movement.setUnitPrice(item.getUnitPrice());
        movement.setRefTable("customer_orders");
        movement.setRefId(orderId);
        movement.setNote("Sale - Invoice deduction");
        movement.setCreatedBy(userId);
        movement.setCreatedAt(LocalDateTime.now());
        stockMovementRepository.save(movement);
    }

    private void deductFIFO(Long branchId, Long itemId, Long variantId, BigDecimal baseQtyToDeduct) {
        List<StockBatch> batches = stockBatchRepository
                .findByBranchIdAndItemIdAndVariantIdOrderByCreatedAtDesc(branchId, itemId, variantId);

        // reverse to get oldest first
        java.util.Collections.reverse(batches);

        BigDecimal remaining = baseQtyToDeduct;
        for (StockBatch batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            if (batch.getQtyRemaining().compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal deduct = remaining.min(batch.getQtyRemaining());
            batch.setQtyRemaining(batch.getQtyRemaining().subtract(deduct));
            batch.setAvailableQty(nvlQty(batch.getAvailableQty()).subtract(deduct));
            stockBatchRepository.save(batch);
            remaining = remaining.subtract(deduct);
        }
    }

    private void reverseStock(Long branchId, OrderProduct op, Long orderId, Long userId) {
        BigDecimal multiplier = itemUnitRepository
                .findByItemIdAndUnitIdAndIsActiveTrue(op.getItemId(), op.getUnitId())
                .map(u -> u.getMultiplierToBase())
                .orElse(BigDecimal.ONE);

        BigDecimal baseQty = op.getQuantity().multiply(multiplier);

        findStock(branchId, op.getItemId(), op.getVariantId()).ifPresent(stock -> {
            stock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(stock);
        });

        if (op.getBatchBarcode() != null) {
            stockBatchRepository.findByBranchIdAndInternalBatchBarcode(branchId, op.getBatchBarcode())
                    .ifPresent(batch -> {
                        batch.setQtyRemaining(batch.getQtyRemaining().add(baseQty));
                        batch.setAvailableQty(nvlQty(batch.getAvailableQty()).add(baseQty));
                        stockBatchRepository.save(batch);
                    });
        }

        StockMovement movement = new StockMovement();
        movement.setBranchId(branchId);
        movement.setMovementType("SALE_CANCEL");
        movement.setItemId(op.getItemId());
        movement.setVariantId(op.getVariantId());
        movement.setUnitId(op.getUnitId());
        movement.setInternalBatchBarcode(op.getBatchBarcode());
        movement.setQuantity(baseQty);
        movement.setUnitPrice(op.getUnitPrice());
        movement.setRefTable("customer_orders");
        movement.setRefId(orderId);
        movement.setNote("Order cancellation - stock reversed");
        movement.setCreatedBy(userId);
        movement.setCreatedAt(LocalDateTime.now());
        stockMovementRepository.save(movement);
    }

    /** {@code baseQty} is the returned quantity in base units; {@code soldUnitId} is the unit the line was sold in. */
    private void addReturnStock(Long branchId, SalesReturnRequest.ReturnItemDto item, BigDecimal baseQty,
                                Long soldUnitId, Long returnId, Long userId) {
        // only add back to stock if condition is GOOD
        if (!"GOOD".equalsIgnoreCase(item.getCondition())) {
            // damaged/expired — move to damaged stock
            findStock(branchId, item.getItemId(), item.getVariantId()).ifPresent(stock -> {
                stock.setLastUpdated(LocalDateTime.now());
                stockRepository.save(stock);
            });
            if (item.getInternalBatchBarcode() != null) {
                stockBatchRepository.findByBranchIdAndInternalBatchBarcode(branchId, item.getInternalBatchBarcode())
                        .ifPresent(batch -> {
                            if ("EXPIRED".equalsIgnoreCase(item.getCondition())) {
                                batch.setExpiredQty(nvlQty(batch.getExpiredQty()).add(baseQty));
                            } else {
                                batch.setDamagedQty(nvlQty(batch.getDamagedQty()).add(baseQty));
                            }
                            stockBatchRepository.save(batch);
                        });
            }
        } else {
            findStock(branchId, item.getItemId(), item.getVariantId()).ifPresent(stock -> {
                stock.setLastUpdated(LocalDateTime.now());
                stockRepository.save(stock);

                if (item.getInternalBatchBarcode() != null) {
                    stockBatchRepository.findByBranchIdAndInternalBatchBarcode(branchId, item.getInternalBatchBarcode())
                            .ifPresent(batch -> {
                                batch.setQtyRemaining(nvlQty(batch.getQtyRemaining()).add(baseQty));
                                batch.setAvailableQty(nvlQty(batch.getAvailableQty()).add(baseQty));
                                stockBatchRepository.save(batch);
                            });
                }
            });
        }

        StockMovement movement = new StockMovement();
        movement.setBranchId(branchId);
        movement.setMovementType("SALE_RETURN");
        movement.setItemId(item.getItemId());
        movement.setVariantId(item.getVariantId());
        // Same convention as the SALE movement: sold unit id, quantity in base units.
        movement.setUnitId(soldUnitId != null ? soldUnitId : 1L);
        movement.setInternalBatchBarcode(item.getInternalBatchBarcode());
        movement.setQuantity(baseQty);
        movement.setUnitPrice(item.getUnitPrice());
        movement.setRefTable("sales_returns");
        movement.setRefId(returnId);
        movement.setNote("Return - condition: " + item.getCondition());
        movement.setCreatedBy(userId);
        movement.setCreatedAt(LocalDateTime.now());
        stockMovementRepository.save(movement);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private Optional<SaleProductSearchResponse> decodeScaleBarcode(Long branchId, String barcode) {
        return scaleBarcodeSettingRepository.findByBranchIdAndIsActiveTrue(branchId)
                .stream()
                .filter(setting -> barcode.startsWith(setting.getPrefix()))
                .filter(setting -> barcode.length() == setting.getTotalLength())
                .findFirst()
                .flatMap(setting -> buildScaleBarcodeResult(branchId, barcode, setting));
    }

    private Optional<SaleProductSearchResponse> buildScaleBarcodeResult(Long branchId, String barcode, ScaleBarcodeSetting setting) {
        String scaleItemCode = substringByOneBasedRange(barcode, setting.getItemCodeStart(), setting.getItemCodeLength());
        String rawValue = substringByOneBasedRange(barcode, setting.getValueStart(), setting.getValueLength());

        ScaleItemMapping mapping = scaleItemMappingRepository
                .findByBranchIdAndScaleItemCodeAndIsActiveTrue(branchId, scaleItemCode)
                .orElse(null);
        if (mapping == null) {
            return Optional.empty();
        }

        Item item = findActiveItem(branchId, mapping.getItemId());
        ItemUnit unit = findUnit(mapping.getItemId(), mapping.getUnitId())
                .orElseThrow(() -> new RuntimeException("Scale item unit not found: " + mapping.getUnitId()));

        BigDecimal decodedValue = new BigDecimal(rawValue).movePointLeft(setting.getValueDecimalPlaces());
        BigDecimal quantity = "WEIGHT".equalsIgnoreCase(setting.getValueType()) ? decodedValue : null;
        BigDecimal encodedPrice = "PRICE".equalsIgnoreCase(setting.getValueType()) ? decodedValue : null;

        SaleProductSearchResponse.ScanDto scan = SaleProductSearchResponse.ScanDto.builder()
                .barcode(barcode)
                .scaleItemCode(scaleItemCode)
                .quantity(quantity)
                .quantityUnit(resolveUnitName(unit))
                .encodedPrice(encodedPrice)
                .build();

        return Optional.of(buildSaleSearchResult("SCALE_BARCODE", item, null, unit, null, scan));
    }

    private SaleProductSearchResponse buildSaleSearchResult(String matchType, Item item, ItemVariant matchedVariant, ItemUnit matchedUnit,
                                                            StockBatch matchedBatch, SaleProductSearchResponse.ScanDto scan) {
        List<ItemUnit> units = itemUnitRepository.findByItemIdAndIsActiveTrue(item.getItemId());
        List<ItemVariant> variants = itemVariantRepository.findByItemIdOrderByVariantIdAsc(item.getItemId())
                .stream()
                .filter(variant -> Boolean.TRUE.equals(variant.getIsActive()))
                .toList();
        Long variantId = matchedVariant != null ? matchedVariant.getVariantId() : matchedBatch != null ? matchedBatch.getVariantId() : null;
        List<StockBatch> batches = variantId != null
                ? stockBatchRepository.findByBranchIdAndItemIdAndVariantIdOrderByCreatedAtDesc(item.getBranchId(), item.getItemId(), variantId)
                : stockBatchRepository.findByBranchIdAndItemIdOrderByCreatedAtDesc(item.getBranchId(), item.getItemId());

        return SaleProductSearchResponse.builder()
                .matchType(matchType)
                .scan(scan)
                .item(mapSearchItem(item))
                .matchedVariant(matchedVariant != null ? mapSearchVariant(matchedVariant) : null)
                .matchedUnit(matchedUnit != null ? mapSearchUnit(matchedUnit) : null)
                .matchedBatch(matchedBatch != null ? mapSearchBatch(matchedBatch) : null)
                .stock(findStock(item.getBranchId(), item.getItemId(), variantId).map(this::mapSearchStock).orElse(null))
                .units(units.stream().map(this::mapSearchUnit).toList())
                .variants(variants.stream().map(this::mapSearchVariant).toList())
                .batches(batches.stream().map(this::mapSearchBatch).toList())
                .promotions(findSalePromotions(item, variantId, matchedBatch, batches))
                .build();
    }

    private List<SaleProductSearchResponse.PromotionDto> findSalePromotions(Item item, Long variantId, StockBatch matchedBatch,
                                                                            List<StockBatch> candidateBatches) {
        List<SaleProductSearchResponse.PromotionDto> result = new ArrayList<>();
        List<Promotion> activePromotions = promotionRepository.findActiveByBranchIdAndNow(item.getBranchId(), LocalDateTime.now());
        List<StockBatch> batchesToMatch = matchedBatch != null ? List.of(matchedBatch) : candidateBatches;

        for (Promotion promotion : activePromotions) {
            promotionItemRepository.findByPromotionIdAndIsActiveTrue(promotion.getPromotionId()).stream()
                    .filter(promotionItem -> promotionItem.getItemId().equals(item.getItemId()))
                    .filter(promotionItem -> promotionItem.getVariantId() == null || promotionItem.getVariantId().equals(variantId))
                    .forEach(promotionItem -> result.add(mapSearchPromotion(promotion, promotionItem, null)));

            if (batchesToMatch != null && !batchesToMatch.isEmpty()) {
                promotionBatchRepository.findByPromotionIdAndIsActiveTrue(promotion.getPromotionId()).stream()
                        .filter(promotionBatch -> batchesToMatch.stream()
                                .anyMatch(batch -> batchPromotionMatches(promotionBatch, batch)))
                        .forEach(promotionBatch -> result.add(mapSearchPromotion(promotion, null, promotionBatch)));
            }
        }

        return result;
    }

    private boolean batchPromotionMatches(PromotionBatch promotionBatch, StockBatch stockBatch) {
        String barcode = promotionBatch.getBarcode();
        return barcode != null && (barcode.equals(stockBatch.getInternalBatchBarcode())
                || barcode.equals(stockBatch.getSupplierBatchBarcode())
                || barcode.equals(stockBatch.getBatchNo()));
    }

    private SaleProductSearchResponse.PromotionDto mapSearchPromotion(Promotion promotion, PromotionItem promotionItem,
                                                                      PromotionBatch promotionBatch) {
        return SaleProductSearchResponse.PromotionDto.builder()
                .promotionId(promotion.getPromotionId())
                .name(promotion.getName())
                .promoCode(promotion.getPromoCode())
                .type(promotion.getType())
                .value(promotion.getValue())
                .priority(promotion.getPriority())
                .isStackable(promotion.getIsStackable())
                .appliesBy(promotionBatch != null ? "BATCH" : "ITEM")
                .promotionItemId(promotionItem != null ? promotionItem.getId() : null)
                .promotionBatchId(promotionBatch != null ? promotionBatch.getId() : null)
                .unitId(promotionItem != null ? promotionItem.getUnitId() : null)
                .variantId(promotionItem != null ? promotionItem.getVariantId() : null)
                .batchBarcode(promotionBatch != null ? promotionBatch.getBarcode() : null)
                .maxQty(promotionItem != null ? promotionItem.getMaxQty() : promotionBatch.getMaxQty())
                .usedQty(promotionItem != null ? promotionItem.getUsedQty() : promotionBatch.getUsedQty())
                .startAt(promotion.getStartAt())
                .endAt(promotion.getEndAt())
                .build();
    }

    private SaleProductSearchResponse.ItemDto mapSearchItem(Item item) {
        return SaleProductSearchResponse.ItemDto.builder()
                .itemId(item.getItemId())
                .branchId(item.getBranchId())
                .sku(item.getSku())
                .name(item.getName())
                .image(item.getImage())
                .categoryId(item.getCategoryId())
                .brandId(item.getBrandId())
                .isWeighed(item.getIsWeighed())
                .scaleBarcodePrefix(item.getScaleBarcodePrefix())
                .isActive(item.getIsActive())
                .minStock(item.getMinStock())
                .maxStock(item.getMaxStock())
                .build();
    }

    private SaleProductSearchResponse.UnitDto mapSearchUnit(ItemUnit unit) {
        return SaleProductSearchResponse.UnitDto.builder()
                .unitId(unit.getUnitId())
                .masterUnitId(unit.getMasterUnitId())
                .unitName(resolveUnitName(unit))
                .multiplierToBase(unit.getMultiplierToBase())
                .barcode(unit.getBarcode())
                .defaultSellingPrice(unit.getDefaultSellingPrice())
                .isBaseUnit(unit.getIsBaseUnit())
                .isActive(unit.getIsActive())
                .build();
    }

    private SaleProductSearchResponse.VariantDto mapSearchVariant(ItemVariant variant) {
        return SaleProductSearchResponse.VariantDto.builder()
                .variantId(variant.getVariantId())
                .sku(variant.getSku())
                .label(resolveVariantLabel(variant.getVariantId()))
                .defaultSellingPrice(variant.getDefaultSellingPrice())
                .isActive(variant.getIsActive())
                .build();
    }

    private SaleProductSearchResponse.StockDto mapSearchStock(Stock stock) {
        return SaleProductSearchResponse.StockDto.builder()
                .stockId(stock.getStockId())
                .variantId(stock.getVariantId())
                .unitId(stock.getUnitId())
                .lastUpdated(stock.getLastUpdated())
                .build();
    }

    private SaleProductSearchResponse.BatchDto mapSearchBatch(StockBatch batch) {
        ItemUnit unit = findUnit(batch.getItemId(), batch.getUnitId()).orElse(null);
        return SaleProductSearchResponse.BatchDto.builder()
                .stockBatchId(batch.getStockBatchId())
                .supplyProductId(batch.getSupplyProductId())
                .variantId(batch.getVariantId())
                .variantSku(resolveVariantSku(batch.getVariantId()))
                .variantLabel(resolveVariantLabel(batch.getVariantId()))
                .unitId(batch.getUnitId())
                .masterUnitId(unit != null ? unit.getMasterUnitId() : null)
                .unitName(resolveUnitName(unit))
                .unitBarcode(unit != null ? unit.getBarcode() : null)
                .receivedQty(batch.getReceivedQty())
                .receivedBaseQty(batch.getReceivedBaseQty())
                .qtyRemaining(batch.getQtyRemaining())
                .availableQty(batch.getAvailableQty())
                .damagedQty(batch.getDamagedQty())
                .expiredQty(batch.getExpiredQty())
                .internalBatchBarcode(batch.getInternalBatchBarcode())
                .batchNo(batch.getBatchNo())
                .supplierBatchBarcode(batch.getSupplierBatchBarcode())
                .expiryDate(batch.getExpiryDate())
                .costPrice(batch.getCostPrice())
                .sellingPrice(batch.getSellingPrice())
                .createdAt(batch.getCreatedAt())
                .build();
    }

    private void addResult(Map<String, SaleProductSearchResponse> results, SaleProductSearchResponse response) {
        if (response == null || response.getItem() == null) {
            return;
        }
        String batchKey = response.getMatchedBatch() != null ? ":batch:" + response.getMatchedBatch().getStockBatchId() : "";
        String variantKey = response.getMatchedVariant() != null ? ":variant:" + response.getMatchedVariant().getVariantId() : "";
        results.putIfAbsent(response.getItem().getItemId() + variantKey + batchKey, response);
    }

    private Item findActiveItem(Long branchId, Long itemId) {
        return itemRepository.findByItemIdAndBranchId(itemId, branchId)
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .orElseThrow(() -> new RuntimeException("Item not found or inactive: " + itemId));
    }

    private Optional<ItemUnit> findUnit(Long itemId, Long unitId) {
        return itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(itemId, unitId);
    }

    private Optional<ItemUnit> findBaseUnit(Long itemId) {
        return itemUnitRepository.findByItemIdAndIsBaseUnitTrueAndIsActiveTrue(itemId);
    }

    private String normalizeSearch(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim().replace(" ", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private String substringByOneBasedRange(String value, Integer start, Integer length) {
        int beginIndex = start - 1;
        int endIndex = beginIndex + length;
        if (beginIndex < 0 || endIndex > value.length()) {
            throw new RuntimeException("Scale barcode setting range is invalid for scanned barcode");
        }
        return value.substring(beginIndex, endIndex);
    }

    private PaymentRequest.PaymentLineDto mapProcessSalePaymentLine(ProcessSaleRequest.PaymentLineDto source) {
        PaymentRequest.PaymentLineDto target = new PaymentRequest.PaymentLineDto();
        target.setPaymentMethod(source.getPaymentMethod());
        target.setAmount(source.getAmount());
        target.setTenderedAmount(source.getTenderedAmount());
        target.setReferenceNo(source.getReferenceNo());
        target.setNote(source.getNote());
        return target;
    }

    private void validateProcessSaleReturnVoucherPayments(List<ProcessSaleRequest.PaymentLineDto> payments) {
        if (payments == null || payments.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> requestedByReturnId = new LinkedHashMap<>();
        for (ProcessSaleRequest.PaymentLineDto line : payments) {
            if (RETURN_VOUCHER_PAYMENT_METHOD.equalsIgnoreCase(line.getPaymentMethod())) {
                addReturnVoucherValidationLine(line.getReferenceNo(), line.getAmount(), requestedByReturnId);
            }
        }
        validateReturnVoucherRequestedAmounts(requestedByReturnId);
    }

    private void validatePaymentRequestReturnVoucherPayments(List<PaymentRequest.PaymentLineDto> payments) {
        if (payments == null || payments.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> requestedByReturnId = new LinkedHashMap<>();
        for (PaymentRequest.PaymentLineDto line : payments) {
            if (RETURN_VOUCHER_PAYMENT_METHOD.equalsIgnoreCase(line.getPaymentMethod())) {
                addReturnVoucherValidationLine(line.getReferenceNo(), line.getAmount(), requestedByReturnId);
            }
        }
        validateReturnVoucherRequestedAmounts(requestedByReturnId);
    }

    private void addReturnVoucherValidationLine(String referenceNo, BigDecimal amount, Map<Long, BigDecimal> requestedByReturnId) {
        Long returnId = resolveReturnVoucherId(referenceNo)
                .orElseThrow(() -> new RuntimeException("Valid return voucher reference is required"));
        BigDecimal requestedAmount = nvl(amount);
        if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Return voucher amount must be greater than 0");
        }
        requestedByReturnId.merge(returnId, requestedAmount, BigDecimal::add);
    }

    private void validateReturnVoucherRequestedAmounts(Map<Long, BigDecimal> requestedByReturnId) {
        for (Map.Entry<Long, BigDecimal> entry : requestedByReturnId.entrySet()) {
            SalesReturn salesReturn = findReturnById(entry.getKey());
            if (!RETURN_VOUCHER_PAYMENT_METHOD.equalsIgnoreCase(salesReturn.getRefundMethod())) {
                throw new RuntimeException("This return was not issued as a return voucher: " + entry.getKey());
            }
            ReturnVoucherResponse voucher = buildReturnVoucherResponse(salesReturn);
            if (entry.getValue().compareTo(nvl(voucher.getRemainingAmount())) > 0) {
                throw new RuntimeException("Return voucher balance is insufficient. Remaining: "
                        + voucher.getRemainingAmount() + ", requested: " + entry.getValue());
            }
        }
    }

    private BigDecimal resolveAppliedPaymentAmount(PaymentRequest.PaymentLineDto line, BigDecimal remainingDue) {
        if (remainingDue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Order is already fully paid");
        }

        BigDecimal requestedAmount = nvl(line.getAmount());
        if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0 && isCashPayment(line.getPaymentMethod())) {
            requestedAmount = nvl(line.getTenderedAmount());
        }

        if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than 0");
        }

        if (requestedAmount.compareTo(remainingDue) > 0) {
            if (isCashPayment(line.getPaymentMethod())) {
                return remainingDue;
            }
            throw new RuntimeException("Payment amount exceeds order balance");
        }

        return requestedAmount;
    }

    private BigDecimal resolveTenderedAmount(PaymentRequest.PaymentLineDto line, BigDecimal appliedAmount) {
        if (!isCashPayment(line.getPaymentMethod())) {
            return line.getTenderedAmount();
        }

        BigDecimal tenderedAmount = line.getTenderedAmount() != null
                ? line.getTenderedAmount()
                : nvl(line.getAmount());

        if (tenderedAmount.compareTo(appliedAmount) < 0) {
            throw new RuntimeException("Cash tendered amount cannot be less than the payment amount");
        }

        return tenderedAmount;
    }

    private BigDecimal resolveChangeAmount(
            PaymentRequest.PaymentLineDto line,
            BigDecimal tenderedAmount,
            BigDecimal appliedAmount
    ) {
        if (!isCashPayment(line.getPaymentMethod()) || tenderedAmount == null) {
            return BigDecimal.ZERO;
        }

        return tenderedAmount.subtract(appliedAmount).max(BigDecimal.ZERO);
    }

    private boolean isCashPayment(String paymentMethod) {
        return "CASH".equalsIgnoreCase(paymentMethod);
    }

    private String generateInvoiceNo(Long branchId) {
        String prefix = "INV-" + branchId + "-";
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uniquePart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String invoiceNo = prefix + datePart + "-" + uniquePart;

        // ensure uniqueness
        while (orderRepository.existsByInvoiceNo(invoiceNo)) {
            uniquePart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            invoiceNo = prefix + datePart + "-" + uniquePart;
        }
        return invoiceNo;
    }

    private String generateOrderNo(Long branchId) {
        String prefix = "SO-" + branchId + "-";
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uniquePart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String orderNo = prefix + datePart + "-" + uniquePart;

        while (orderRepository.existsByOrderNo(orderNo)) {
            uniquePart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            orderNo = prefix + datePart + "-" + uniquePart;
        }
        return orderNo;
    }

    private String resolveOrderNo(CustomerOrder order) {
        if (order.getOrderNo() != null && !order.getOrderNo().isBlank()) {
            return order.getOrderNo();
        }
        return order.getOrderId() != null ? String.format("SO-%03d", order.getOrderId()) : null;
    }

    private void recordStatusHistory(Long orderId, String oldStatus, String newStatus, Long changedBy, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orderId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(changedBy);
        history.setNote(note);
        history.setChangedAt(LocalDateTime.now());
        statusHistoryRepository.save(history);
    }

    private CustomerOrder findOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
    }

    private SalesReturnLedger buildReturnLedger(CustomerOrder order) {
        return buildReturnLedger(order, orderProductRepository.findByOrderId(order.getOrderId()));
    }

    private SalesReturnLedger buildReturnLedger(CustomerOrder order, List<OrderProduct> products) {
        List<Long> priorReturnIds = returnRepository.findByOrderId(order.getOrderId()).stream()
                .filter(r -> !"CANCELLED".equalsIgnoreCase(r.getStatus()))
                .map(SalesReturn::getReturnId)
                .toList();
        List<SalesReturnItem> priorItems = priorReturnIds.isEmpty()
                ? List.of()
                : returnItemRepository.findByReturnIdIn(priorReturnIds);
        return SalesReturnLedger.build(order, products, priorItems);
    }

    /**
     * Multiplier from the line's sale unit to base units. Falls back to the unit row even if it was deactivated
     * after the sale, so a return converts at the same rate the sale was deducted at.
     */
    private BigDecimal baseUnitMultiplier(OrderProduct soldLine) {
        if (soldLine.getUnitId() == null) {
            return BigDecimal.ONE;
        }
        return itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(soldLine.getItemId(), soldLine.getUnitId())
                .or(() -> itemUnitRepository.findById(soldLine.getUnitId()))
                .map(ItemUnit::getMultiplierToBase)
                .filter(multiplier -> multiplier != null && multiplier.signum() > 0)
                .orElse(BigDecimal.ONE);
    }

    private record ReturnSettlement(BigDecimal balanceAdjustment, BigDecimal refund, BigDecimal accountCredit,
                                    BigDecimal creditReversal, BigDecimal paid, BigDecimal adjustedAfter) {
    }

    /**
     * Splits a return's value between what the customer still owes and money paid back.
     * The value first clears any balance still due on the order (so unpaid goods are not refunded).
     * Anything left is given back, never more than what was actually paid less earlier refunds.
     * The share paid on the customer's credit account always goes back to that account, not out as cash.
     * With BALANCE_ADJUSTMENT the whole amount given back is credited to the account.
     */
    private ReturnSettlement settleReturn(CustomerOrder order, List<PlannedReturnLine> plan, String refundMethod) {
        BigDecimal value = plan.stream().map(p -> p.allocation().refund())
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        List<Payment> payments = paymentRepository.findByOrderId(order.getOrderId());
        BigDecimal paid = payments.stream()
                .map(Payment::getAmount).map(this::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditPaid = creditPaid(payments);
        List<SalesReturn> prior = activeReturns(order.getOrderId());
        BigDecimal priorAdjusted = prior.stream().map(SalesReturn::getBalanceAdjustment).map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal priorRefunded = prior.stream().map(this::givenBack).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal priorReversed = prior.stream().map(SalesReturn::getCreditReversal).map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balanceDue = nvl(order.getTotal()).subtract(priorAdjusted).subtract(paid).max(BigDecimal.ZERO);
        BigDecimal adjustment = value.min(balanceDue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundable = paid.subtract(priorRefunded).max(BigDecimal.ZERO);
        BigDecimal payout = value.subtract(adjustment).min(refundable).setScale(2, RoundingMode.HALF_UP);

        BigDecimal creditReversal = payout.min(creditPaid.subtract(priorReversed).max(BigDecimal.ZERO));
        BigDecimal rest = payout.subtract(creditReversal);
        boolean toAccount = BALANCE_ADJUSTMENT_REFUND_METHOD.equalsIgnoreCase(refundMethod);
        BigDecimal accountCredit = toAccount ? payout : creditReversal;
        BigDecimal refund = toAccount ? BigDecimal.ZERO.setScale(2) : rest;
        return new ReturnSettlement(adjustment, refund, accountCredit, creditReversal, paid,
                priorAdjusted.add(adjustment));
    }

    /** Everything a return gave back: paid out plus credited to the customer's account. */
    private BigDecimal givenBack(SalesReturn r) {
        return nvl(r.getRefundAmount()).add(nvl(r.getAccountCredit()));
    }

    private BigDecimal creditPaid(List<Payment> payments) {
        return payments.stream()
                .filter(p -> "CREDIT".equalsIgnoreCase(p.getPaymentMethod()))
                .map(Payment::getAmount).map(this::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<SalesReturn> activeReturns(Long orderId) {
        return returnRepository.findByOrderId(orderId).stream()
                .filter(r -> !"CANCELLED".equalsIgnoreCase(r.getStatus()))
                .toList();
    }

    private BigDecimal returnBalanceAdjustment(Long orderId) {
        return activeReturns(orderId).stream().map(SalesReturn::getBalanceAdjustment).map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** After a return writes off part of the balance, the order may now be settled. */
    private void refreshPaymentStatus(CustomerOrder order, ReturnSettlement settlement) {
        BigDecimal due = nvl(order.getTotal()).subtract(settlement.adjustedAfter());
        if (settlement.paid().compareTo(due) >= 0) {
            order.setPaymentStatus("PAID");
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        }
    }

    private record PlannedReturnLine(SalesReturnRequest.ReturnItemDto request, SalesReturnLedger.Allocation allocation) {
    }

    private SalesReturn findReturnById(Long returnId) {
        return returnRepository.findById(returnId)
                .orElseThrow(() -> new RuntimeException("Return not found: " + returnId));
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // ─── Mappers ─────────────────────────────────────────────────────────────────

    private OrderResponse buildOrderResponse(CustomerOrder order) {
        List<AppliedPromotionResponse> appliedPromotions = salePromotionService.getAppliedPromotions(order.getOrderId());
        List<OrderProduct> products = orderProductRepository.findByOrderId(order.getOrderId());
        SalesReturnLedger ledger = buildReturnLedger(order, products);
        List<OrderProductResponse> items = products.stream()
                .map(op -> withReturnPosition(mapOrderProduct(op), ledger.line(op.getOrderProductId())))
                .toList();

        List<Payment> paymentRows = paymentRepository.findByOrderId(order.getOrderId());
        List<PaymentResponse> payments = paymentRows.stream().map(this::mapPayment).toList();
        BigDecimal amountPaid = paymentRows.stream().map(Payment::getAmount).map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SalesReturn> activeReturns = activeReturns(order.getOrderId());
        BigDecimal adjusted = activeReturns.stream().map(SalesReturn::getBalanceAdjustment).map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = activeReturns.stream().map(this::givenBack).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditReversed = activeReturns.stream().map(SalesReturn::getCreditReversal).map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .branchId(order.getBranchId())
                .invoiceNo(order.getInvoiceNo())
                .orderNo(resolveOrderNo(order))
                .userId(order.getUserId())
                .customerId(order.getCustomerId())
                .cashSessionId(order.getCashSessionId())
                .subtotal(order.getSubtotal())
                .discount(order.getDiscount())
                .taxAmount(order.getTaxAmount())
                .rounding(order.getRounding())
                .total(order.getTotal())
                .paymentStatus(order.getPaymentStatus())
                .amountPaid(amountPaid)
                .returnBalanceAdjustment(adjusted)
                .refundedAmount(refunded)
                .creditPaidAmount(creditPaid(paymentRows))
                .creditReversedAmount(creditReversed)
                .balanceDue(nvl(order.getTotal()).subtract(adjusted).subtract(amountPaid).max(BigDecimal.ZERO))
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .notes(order.getNotes())
                .items(items)
                .payments(payments)
                .appliedPromotions(appliedPromotions)
                .promotionDiscount(appliedPromotions.stream().map(AppliedPromotionResponse::getDiscountAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }

    private CustomerOrderViewResponse buildCustomerOrderViewResponse(CustomerOrder order) {
        List<OrderProduct> products = orderProductRepository.findByOrderId(order.getOrderId());
        List<CustomerOrderItemResponse> items = products.stream()
                .map(this::mapCustomerOrderItem)
                .toList();
        BigDecimal itemCount = products.stream()
                .map(product -> nvl(product.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CustomerOrderViewResponse.builder()
                .orderId(order.getOrderId())
                .invoiceNo(order.getInvoiceNo())
                .orderNo(resolveOrderNo(order))
                .createdAt(order.getOrderDate())
                .paymentMethod(resolvePaymentMethod(order.getOrderId()))
                .items(items)
                .itemCount(itemCount)
                .totalAmount(order.getTotal())
                .status(order.getStatus())
                .build();
    }

    private CustomerOrderItemResponse mapCustomerOrderItem(OrderProduct op) {
        String itemName = itemRepository.findById(op.getItemId())
                .map(Item::getName)
                .orElse(null);
        ItemUnit unit = itemUnitRepository.findById(op.getUnitId()).orElse(null);

        return CustomerOrderItemResponse.builder()
                .itemId(op.getItemId())
                .variantId(op.getVariantId())
                .variantSku(resolveVariantSku(op.getVariantId()))
                .variantLabel(resolveVariantLabel(op.getVariantId()))
                .itemName(itemName)
                .quantity(op.getQuantity())
                .unitName(resolveUnitName(unit))
                .unitPrice(op.getUnitPrice())
                .lineTotal(op.getLineTotal())
                .build();
    }

    private String resolvePaymentMethod(Long orderId) {
        List<String> paymentMethods = paymentRepository.findByOrderId(orderId).stream()
                .map(Payment::getPaymentMethod)
                .filter(method -> method != null && !method.isBlank())
                .distinct()
                .toList();

        if (paymentMethods.isEmpty()) {
            return null;
        }
        return paymentMethods.size() == 1 ? paymentMethods.get(0) : "MIXED";
    }

    private String formatPageSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return "";
        }
        return pageable.getSort().stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }

    private OrderProductResponse mapOrderProduct(OrderProduct op) {
        OrderProductPromotion promotion = salePromotionService.getLinePromotion(op.getOrderProductId());
        String itemName = itemRepository.findById(op.getItemId())
                .map(i -> i.getName()).orElse(null);
        ItemUnit unit = itemUnitRepository.findById(op.getUnitId()).orElse(null);

        return OrderProductResponse.builder()
                .orderProductId(op.getOrderProductId())
                .orderId(op.getOrderId())
                .itemId(op.getItemId())
                .variantId(op.getVariantId())
                .variantSku(resolveVariantSku(op.getVariantId()))
                .variantLabel(resolveVariantLabel(op.getVariantId()))
                .itemName(itemName)
                .unitId(op.getUnitId())
                .masterUnitId(unit != null ? unit.getMasterUnitId() : null)
                .unitName(resolveUnitName(unit))
                .batchBarcode(op.getBatchBarcode())
                .quantity(op.getQuantity())
                .unitPrice(op.getUnitPrice())
                .discount(op.getDiscount())
                .promotionId(promotion != null ? promotion.getPromotionId() : null)
                .promotionName(promotion != null ? promotion.getPromotionName() : null)
                .promotionType(promotion != null ? promotion.getPromoType() : null)
                .promotionValue(promotion != null ? promotion.getPromoValue() : null)
                .lineTotal(op.getLineTotal())
                .createdAt(op.getCreatedAt())
                .build();
    }

    private OrderProductResponse withReturnPosition(OrderProductResponse response, SalesReturnLedger.Line line) {
        if (line == null) {
            return response;
        }
        response.setNetLineTotal(line.netTotal());
        response.setNetUnitPrice(line.netUnitPrice());
        response.setReturnedQuantity(line.returnedQuantity());
        response.setReturnableQuantity(line.remainingQuantity());
        response.setRefundedAmount(line.refundedAmount());
        response.setRefundableAmount(line.refundableAmount());
        return response;
    }

    private String resolveUnitName(ItemUnit unit) {
        if (unit == null) {
            return null;
        }

        if (unit.getMasterUnitId() != null) {
            return unitMasterRepository.findById(unit.getMasterUnitId())
                    .map(UnitMaster::getName)
                    .orElse(unit.getUnitName());
        }

        return unit.getUnitName();
    }

    private PaymentResponse mapPayment(Payment p) {
        return PaymentResponse.builder()
                .paymentId(p.getPaymentId())
                .orderId(p.getOrderId())
                .cashSessionId(p.getCashSessionId())
                .amount(p.getAmount())
                .tenderedAmount(p.getTenderedAmount())
                .changeAmount(p.getChangeAmount())
                .paymentMethod(p.getPaymentMethod())
                .paymentDate(p.getPaymentDate())
                .receivedBy(p.getReceivedBy())
                .referenceNo(p.getReferenceNo())
                .note(p.getNote())
                .build();
    }

    private Optional<Long> resolveReturnVoucherId(String voucherNoOrCode) {
        if (voucherNoOrCode == null || voucherNoOrCode.isBlank()) {
            return Optional.empty();
        }

        String normalized = voucherNoOrCode.trim().toUpperCase();
        String candidate = normalized
                .replaceFirst("^(RV|RET|RETURN|VOUCHER)[\\s#-]*", "")
                .trim();

        if (!candidate.matches("\\d+")) {
            candidate = normalized.replaceFirst("^.*?(\\d+)$", "$1");
        }

        if (!candidate.matches("\\d+")) {
            return Optional.empty();
        }

        return Optional.of(Long.parseLong(candidate));
    }

    private String getReturnVoucherNo(Long returnId) {
        return "RV-" + returnId;
    }

    private List<String> getReturnVoucherReferenceNos(Long returnId) {
        String id = String.valueOf(returnId);
        return List.of(getReturnVoucherNo(returnId), "RET-" + id, "RETURN-" + id, id);
    }

    private BigDecimal getReturnVoucherUsedAmount(Long returnId) {
        return paymentRepository
                .findByPaymentMethodIgnoreCaseAndReferenceNoIn(RETURN_VOUCHER_PAYMENT_METHOD, getReturnVoucherReferenceNos(returnId))
                .stream()
                .map(payment -> nvl(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ReturnVoucherResponse buildReturnVoucherResponse(SalesReturn r) {
        BigDecimal originalAmount = nvl(r.getRefundAmount());
        BigDecimal usedAmount = getReturnVoucherUsedAmount(r.getReturnId()).min(originalAmount);
        BigDecimal remainingAmount = originalAmount.subtract(usedAmount).max(BigDecimal.ZERO);
        String status = remainingAmount.compareTo(BigDecimal.ZERO) <= 0
                ? "USED"
                : usedAmount.compareTo(BigDecimal.ZERO) > 0 ? "PARTIALLY_USED" : "ACTIVE";

        return ReturnVoucherResponse.builder()
                .returnId(r.getReturnId())
                .orderId(r.getOrderId())
                .branchId(r.getBranchId())
                .customerId(r.getCustomerId())
                .voucherNo(getReturnVoucherNo(r.getReturnId()))
                .redemptionCode(getReturnVoucherNo(r.getReturnId()))
                .originalAmount(originalAmount)
                .usedAmount(usedAmount)
                .remainingAmount(remainingAmount)
                .status(status)
                .returnDate(r.getReturnDate())
                .build();
    }

    private SalesReturnResponse buildReturnResponse(SalesReturn r) {
        String invoiceNo = orderRepository.findById(r.getOrderId())
                .map(CustomerOrder::getInvoiceNo).orElse(null);
        ReturnVoucherResponse voucher = RETURN_VOUCHER_PAYMENT_METHOD.equalsIgnoreCase(r.getRefundMethod())
                ? buildReturnVoucherResponse(r)
                : null;

        List<SalesReturnResponse.ReturnItemResponse> items = returnItemRepository.findByReturnId(r.getReturnId())
                .stream().map(ri -> {
                    String itemName = itemRepository.findById(ri.getItemId())
                            .map(i -> i.getName()).orElse(null);
                    return SalesReturnResponse.ReturnItemResponse.builder()
                            .returnItemId(ri.getReturnItemId())
                            .orderProductId(ri.getOrderProductId())
                            .itemId(ri.getItemId())
                            .variantId(ri.getVariantId())
                            .variantSku(resolveVariantSku(ri.getVariantId()))
                            .variantLabel(resolveVariantLabel(ri.getVariantId()))
                            .itemName(itemName)
                            .internalBatchBarcode(ri.getInternalBatchBarcode())
                            .quantity(ri.getQuantity())
                            .unitPrice(ri.getUnitPrice())
                            .lineRefund(ri.getLineRefund())
                            .condition(ri.getCondition())
                            .build();
                }).toList();

        return SalesReturnResponse.builder()
                .returnId(r.getReturnId())
                .orderId(r.getOrderId())
                .invoiceNo(invoiceNo)
                .branchId(r.getBranchId())
                .customerId(r.getCustomerId())
                .returnDate(r.getReturnDate())
                .refundMethod(r.getRefundMethod())
                .refundAmount(r.getRefundAmount())
                .returnValue(r.getReturnValue() != null ? r.getReturnValue() : r.getRefundAmount())
                .balanceAdjustment(nvl(r.getBalanceAdjustment()))
                .accountCredit(nvl(r.getAccountCredit()))
                .voucher(voucher)
                .voucherNo(voucher != null ? voucher.getVoucherNo() : null)
                .voucherRemainingAmount(voucher != null ? voucher.getRemainingAmount() : null)
                .reason(r.getReason())
                .processedBy(r.getProcessedBy())
                .status(r.getStatus())
                .items(items)
                .build();
    }

    private BigDecimal nvlQty(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal getBatchQtyTotal(Long branchId, Long itemId, Long variantId, StockQtyType type) {
        return stockBatchRepository.findByBranchIdAndItemIdAndVariantId(branchId, itemId, variantId)
                .stream()
                .map(batch -> switch (type) {
                    case AVAILABLE -> nvlQty(batch.getAvailableQty() != null ? batch.getAvailableQty() : batch.getQtyRemaining());
                    case DAMAGED -> nvlQty(batch.getDamagedQty());
                    case EXPIRED -> nvlQty(batch.getExpiredQty());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Optional<Stock> findStock(Long branchId, Long itemId, Long variantId) {
        return stockRepository.findByBranchIdAndItemIdAndVariantId(branchId, itemId, variantId);
    }

    private Optional<ItemVariant> resolveVariant(Long variantId) {
        return variantId == null ? Optional.empty() : itemVariantRepository.findById(variantId);
    }

    private String resolveVariantSku(Long variantId) {
        return resolveVariant(variantId)
                .map(ItemVariant::getSku)
                .orElse(null);
    }

    private String resolveVariantLabel(Long variantId) {
        if (variantId == null) {
            return null;
        }
        String label = itemVariantAttributeRepository.findByVariantIdOrderByAttributeNameAsc(variantId)
                .stream()
                .map(attribute -> attribute.getAttributeName() + ": " + attribute.getAttributeValue())
                .reduce((left, right) -> left + " / " + right)
                .orElse(null);
        return label != null && !label.isBlank() ? label : resolveVariantSku(variantId);
    }

    private Long resolveOrderLineVariantId(Long branchId, OrderProductRequest item) {
        Long batchVariantId = resolveBatchVariantId(branchId, item.getItemId(), item.getBatchBarcode());
        Long variantId = batchVariantId != null ? batchVariantId : item.getVariantId();
        validateVariant(branchId, item.getItemId(), variantId);
        return variantId;
    }

    private Long resolveReturnLineVariantId(Long branchId, SalesReturnRequest.ReturnItemDto item) {
        Long batchVariantId = resolveBatchVariantId(branchId, item.getItemId(), item.getInternalBatchBarcode());
        Long variantId = batchVariantId != null ? batchVariantId : item.getVariantId();
        validateVariant(branchId, item.getItemId(), variantId);
        return variantId;
    }

    private Long resolveBatchVariantId(Long branchId, Long itemId, String batchBarcode) {
        if (batchBarcode == null || batchBarcode.isBlank()) {
            return null;
        }
        return stockBatchRepository.findByBranchIdAndInternalBatchBarcode(branchId, batchBarcode)
                .filter(batch -> itemId.equals(batch.getItemId()))
                .map(StockBatch::getVariantId)
                .orElse(null);
    }

    private void validateVariant(Long branchId, Long itemId, Long variantId) {
        if (variantId == null) {
            return;
        }
        ItemVariant variant = itemVariantRepository.findByVariantIdAndItemId(variantId, itemId)
                .orElseThrow(() -> new RuntimeException("Variant not found for item: " + variantId));
        if (!variant.getBranchId().equals(branchId)) {
            throw new RuntimeException("Variant does not belong to branch: " + variantId);
        }
        if (!Boolean.TRUE.equals(variant.getIsActive())) {
            throw new RuntimeException("Variant is inactive: " + variantId);
        }
    }

    private boolean sameVariant(Long left, Long right) {
        return java.util.Objects.equals(left, right);
    }

    private enum StockQtyType {
        AVAILABLE,
        DAMAGED,
        EXPIRED
    }
}
