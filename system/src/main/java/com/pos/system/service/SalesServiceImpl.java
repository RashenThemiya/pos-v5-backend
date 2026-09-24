package com.pos.system.service;

import com.pos.system.dto.customer.CustomerResponse;
import com.pos.system.dto.sale.*;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.ItemVariant;
import com.pos.system.model.catalog.ScaleBarcodeSetting;
import com.pos.system.model.catalog.ScaleItemMapping;
import com.pos.system.model.catalog.UnitMaster;
import com.pos.system.model.cash.CashSessionTransaction;
import com.pos.system.model.customer.Customer;
import com.pos.system.model.promotion.Promotion;
import com.pos.system.model.promotion.PromotionBatch;
import com.pos.system.model.promotion.PromotionItem;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SalesServiceImpl implements SalesService {

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
    private final CreditNoteRepository creditNoteRepository;
    private final ReturnVoucherRepository returnVoucherRepository;
    private final ReturnVoucherTransactionRepository returnVoucherTransactionRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;

    // ─── Orders ──────────────────────────────────────────────────────────────────

    @Override
    public OrderResponse processSale(ProcessSaleRequest request) {
        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setBranchId(request.getBranchId());
        orderRequest.setUserId(request.getUserId());
        orderRequest.setCustomerId(request.getCustomerId());
        orderRequest.setCashSessionId(request.getCashSessionId());
        orderRequest.setItems(request.getItems());
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

        BigDecimal orderTotal = nvl(order.getTotal());
        BigDecimal runningPaid = paymentRepository.findByOrderId(orderId).stream()
                .map(payment -> nvl(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (runningPaid.compareTo(orderTotal) >= 0) {
            throw new RuntimeException("Order is already fully paid");
        }

        for (PaymentRequest.PaymentLineDto line : request.getPayments()) {
            String paymentMethod = normalizePaymentMethod(line.getPaymentMethod());
            BigDecimal remainingDue = orderTotal.subtract(runningPaid).max(BigDecimal.ZERO);
            BigDecimal appliedAmount = resolveAppliedPaymentAmount(line, remainingDue);
            BigDecimal tenderedAmount = resolveTenderedAmount(line, appliedAmount);
            String referenceNo = "RETURN_VOUCHER".equals(paymentMethod)
                    ? normalizeVoucherCode(line.getVoucherCode() != null ? line.getVoucherCode() : line.getReferenceNo())
                    : line.getReferenceNo();

            Payment payment = new Payment();
            payment.setBranchId(order.getBranchId());
            payment.setOrderId(orderId);
            payment.setCustomerId(order.getCustomerId());
            payment.setCashSessionId(order.getCashSessionId());
            payment.setAmount(appliedAmount);
            payment.setTenderedAmount(tenderedAmount);
            payment.setChangeAmount(resolveChangeAmount(line, tenderedAmount, appliedAmount));
            payment.setPaymentMethod(paymentMethod);
            payment.setPaymentDate(LocalDateTime.now());
            payment.setReceivedBy(request.getReceivedBy());
            payment.setReferenceNo(referenceNo);
            payment.setNote(line.getNote());
            Payment savedPayment = paymentRepository.save(payment);

            runningPaid = runningPaid.add(appliedAmount);

            if ("CREDIT".equals(paymentMethod)) {
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
            } else if ("CUSTOMER_BALANCE".equals(paymentMethod)) {
                if (order.getCustomerId() == null) {
                    throw new RuntimeException("Customer is required for customer credit payments");
                }
                customerService.applyCustomerCreditPayment(
                        order.getCustomerId(),
                        appliedAmount,
                        order.getOrderId(),
                        order.getInvoiceNo(),
                        request.getReceivedBy()
                );
            } else if ("RETURN_VOUCHER".equals(paymentMethod)) {
                redeemReturnVoucherForSale(referenceNo, appliedAmount, order, request.getReceivedBy());
            }

            // write cash session transaction
            if (order.getCashSessionId() != null) {
                CashSessionTransaction txn = new CashSessionTransaction();
                txn.setSessionId(order.getCashSessionId());
                txn.setType("SALE");
                txn.setAmount(appliedAmount);
                txn.setPaymentMethod(paymentMethod);
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
        if (request == null) {
            throw new RuntimeException("Return request is required");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Return must contain at least one item");
        }

        CustomerOrder order = findOrderById(request.getOrderId());

        if ("CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("Cannot return items from a cancelled order");
        }
        if (request.getBranchId() != null && !request.getBranchId().equals(order.getBranchId())) {
            throw new RuntimeException("Return branch does not match the original invoice branch");
        }

        Long customerId = request.getCustomerId() != null ? request.getCustomerId() : order.getCustomerId();
        String settlementMethod = normalizeSettlementMethod(
                request.getSettlementMethod() != null ? request.getSettlementMethod() : request.getRefundMethod(),
                customerId
        );

        BigDecimal totalRefund = BigDecimal.ZERO;

        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setBranchId(order.getBranchId());
        salesReturn.setOrderId(request.getOrderId());
        salesReturn.setReturnNo(generateReturnNo(order.getBranchId()));
        salesReturn.setCustomerId(customerId);
        salesReturn.setCashSessionId(request.getCashSessionId() != null ? request.getCashSessionId() : order.getCashSessionId());
        salesReturn.setReturnDate(LocalDateTime.now());
        salesReturn.setRefundMethod(settlementMethod);
        salesReturn.setSettlementMethod(settlementMethod);
        salesReturn.setExchangeOrderId(request.getExchangeOrderId());
        salesReturn.setReason(request.getReason());
        salesReturn.setProcessedBy(request.getProcessedBy());
        salesReturn.setStatus("COMPLETED");
        salesReturn.setCompletedAt(LocalDateTime.now());

        SalesReturn savedReturn = returnRepository.save(salesReturn);

        for (SalesReturnRequest.ReturnItemDto item : request.getItems()) {
            OrderProduct originalLine = resolveOriginalReturnLine(order, item);
            BigDecimal requestedQty = positiveQuantity(item.getQuantity());
            BigDecimal previouslyReturned = getPreviouslyReturnedQuantity(order.getOrderId(), originalLine);
            BigDecimal returnableQty = nvl(originalLine.getQuantity()).subtract(previouslyReturned);

            if (returnableQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Item is already fully returned: " + originalLine.getItemId());
            }
            if (requestedQty.compareTo(returnableQty) > 0) {
                throw new RuntimeException("Return quantity exceeds returnable quantity for item "
                        + originalLine.getItemId() + ". Returnable: " + returnableQty + ", requested: " + requestedQty);
            }

            BigDecimal unitRefund = calculateRefundUnitPrice(order, originalLine);
            BigDecimal lineRefund = unitRefund.multiply(requestedQty).setScale(2, RoundingMode.HALF_UP);
            totalRefund = totalRefund.add(lineRefund);

            SalesReturnItem returnItem = new SalesReturnItem();
            returnItem.setReturnId(savedReturn.getReturnId());
            returnItem.setOrderProductId(originalLine.getOrderProductId());
            returnItem.setItemId(originalLine.getItemId());
            returnItem.setVariantId(originalLine.getVariantId());
            returnItem.setUnitId(originalLine.getUnitId());
            returnItem.setInternalBatchBarcode(originalLine.getBatchBarcode());
            returnItem.setQuantity(requestedQty);
            returnItem.setUnitPrice(unitRefund);
            returnItem.setLineRefund(lineRefund);
            returnItem.setCondition(normalizeReturnCondition(item.getCondition()));
            returnItem.setPreviouslyReturnedQuantity(previouslyReturned);
            returnItem.setReturnableQuantity(returnableQty);
            returnItemRepository.save(returnItem);

            // add stock back
            item.setItemId(originalLine.getItemId());
            item.setVariantId(originalLine.getVariantId());
            item.setUnitId(originalLine.getUnitId());
            item.setInternalBatchBarcode(originalLine.getBatchBarcode());
            item.setQuantity(requestedQty);
            item.setUnitPrice(unitRefund);
            item.setCondition(returnItem.getCondition());
            addReturnStock(order.getBranchId(), item, savedReturn.getReturnId(), request.getProcessedBy());
        }

        totalRefund = totalRefund.setScale(2, RoundingMode.HALF_UP);
        savedReturn.setRefundAmount(totalRefund);

        CreditNote creditNote = createCreditNote(order, savedReturn, totalRefund, settlementMethod, request.getProcessedBy());
        savedReturn.setCreditNoteNo(creditNote.getCreditNoteNo());

        if ("CUSTOMER_CREDIT".equals(settlementMethod) || ("EXCHANGE".equals(settlementMethod) && customerId != null)) {
            if (customerId == null) {
                throw new RuntimeException("Customer is required for customer credit settlement");
            }
            customerService.recordReturnCredit(
                    customerId,
                    totalRefund,
                    savedReturn.getReturnId(),
                    savedReturn.getReturnNo(),
                    request.getProcessedBy()
            );
        } else if ("RETURN_VOUCHER".equals(settlementMethod) || "EXCHANGE".equals(settlementMethod)) {
            ReturnVoucher voucher = createReturnVoucher(order, savedReturn, creditNote, totalRefund, request.getProcessedBy());
            savedReturn.setVoucherNo(voucher.getVoucherNo());
        }

        returnRepository.save(savedReturn);

        // write cash session transaction for cash refund
        if ("CASH".equals(settlementMethod) && savedReturn.getCashSessionId() != null) {
            CashSessionTransaction txn = new CashSessionTransaction();
            txn.setSessionId(savedReturn.getCashSessionId());
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

        refreshOrderReturnStatus(order);

        return buildReturnResponse(savedReturn);
    }

    @Override
    public SalesReturnResponse getReturnById(Long returnId) {
        return buildReturnResponse(findReturnById(returnId));
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
    public ReturnVoucherResponse getReturnVoucher(String voucherNoOrCode) {
        String code = normalizeVoucherCode(voucherNoOrCode);
        ReturnVoucher voucher = returnVoucherRepository.findByVoucherNo(code)
                .or(() -> returnVoucherRepository.findByRedemptionCode(code))
                .orElseThrow(() -> new RuntimeException("Return voucher not found"));
        return mapReturnVoucher(voucher, true);
    }

    public Page<SalesReturnResponse> getReturnsByBranch(Long branchId, Pageable pageable) {
        return returnRepository.findByBranchId(branchId, pageable)
                .map(this::buildReturnResponse);
    }

    // ─── Stock deduction ─────────────────────────────────────────────────────────

    private String normalizeSettlementMethod(String method, Long customerId) {
        String normalized = method == null || method.isBlank()
                ? "CASH"
                : method.trim().toUpperCase();

        if ("BALANCE_ADJUSTMENT".equals(normalized) || "STORE_CREDIT".equals(normalized)) {
            normalized = customerId != null ? "CUSTOMER_CREDIT" : "RETURN_VOUCHER";
        }
        if ("CREDIT".equals(normalized) || "CUSTOMER_BALANCE".equals(normalized)) {
            normalized = "CUSTOMER_CREDIT";
        }
        if ("VOUCHER".equals(normalized) || "CREDIT_VOUCHER".equals(normalized)) {
            normalized = "RETURN_VOUCHER";
        }

        return switch (normalized) {
            case "CASH", "CARD", "BANK", "CUSTOMER_CREDIT", "RETURN_VOUCHER", "EXCHANGE" -> normalized;
            default -> throw new RuntimeException("Unsupported return settlement method: " + normalized);
        };
    }

    private String normalizeReturnCondition(String condition) {
        if (condition == null || condition.isBlank()) {
            return "GOOD";
        }
        String normalized = condition.trim().toUpperCase();
        return switch (normalized) {
            case "GOOD", "SELLABLE" -> "GOOD";
            case "DAMAGED", "DEFECTIVE" -> "DAMAGED";
            case "EXPIRED" -> "EXPIRED";
            default -> "OTHER";
        };
    }

    private OrderProduct resolveOriginalReturnLine(CustomerOrder order, SalesReturnRequest.ReturnItemDto item) {
        if (item.getOrderProductId() != null) {
            OrderProduct line = orderProductRepository.findById(item.getOrderProductId())
                    .orElseThrow(() -> new RuntimeException("Original sale line not found: " + item.getOrderProductId()));
            if (!order.getOrderId().equals(line.getOrderId())) {
                throw new RuntimeException("Return item does not belong to the original invoice");
            }
            return line;
        }

        List<OrderProduct> candidates = orderProductRepository.findByOrderIdAndItemId(order.getOrderId(), item.getItemId());
        List<OrderProduct> matching = candidates.stream()
                .filter(line -> Objects.equals(line.getVariantId(), item.getVariantId()))
                .filter(line -> sameBlankable(line.getBatchBarcode(), item.getInternalBatchBarcode()))
                .toList();

        if (matching.size() == 1) {
            return matching.get(0);
        }
        if (matching.isEmpty()) {
            throw new RuntimeException("Returned item was not found on the original invoice: " + item.getItemId());
        }
        throw new RuntimeException("Return item is ambiguous. Send the original orderProductId for item: " + item.getItemId());
    }

    private boolean sameBlankable(String left, String right) {
        String normalizedLeft = left == null || left.isBlank() ? null : left.trim();
        String normalizedRight = right == null || right.isBlank() ? null : right.trim();
        return Objects.equals(normalizedLeft, normalizedRight);
    }

    private BigDecimal positiveQuantity(BigDecimal quantity) {
        BigDecimal normalized = nvl(quantity);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Return quantity must be greater than zero");
        }
        return normalized;
    }

    private BigDecimal getPreviouslyReturnedQuantity(Long orderId, OrderProduct originalLine) {
        BigDecimal returned = returnItemRepository.sumReturnedQuantity(
                orderId,
                originalLine.getOrderProductId(),
                originalLine.getItemId(),
                originalLine.getVariantId(),
                originalLine.getBatchBarcode()
        );
        return nvl(returned);
    }

    private BigDecimal calculateRefundUnitPrice(CustomerOrder order, OrderProduct originalLine) {
        BigDecimal soldQty = nvl(originalLine.getQuantity());
        if (soldQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Original sold quantity is invalid for line: " + originalLine.getOrderProductId());
        }

        BigDecimal lineTotal = nvl(originalLine.getLineTotal()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal orderSubtotal = nvl(order.getSubtotal());
        BigDecimal orderDiscount = nvl(order.getDiscount());
        BigDecimal allocatedOrderDiscount = BigDecimal.ZERO;

        if (orderSubtotal.compareTo(BigDecimal.ZERO) > 0 && orderDiscount.compareTo(BigDecimal.ZERO) > 0) {
            allocatedOrderDiscount = lineTotal
                    .divide(orderSubtotal, 8, RoundingMode.HALF_UP)
                    .multiply(orderDiscount)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal refundableLineTotal = lineTotal.subtract(allocatedOrderDiscount).max(BigDecimal.ZERO);
        return refundableLineTotal.divide(soldQty, 6, RoundingMode.HALF_UP);
    }

    private CreditNote createCreditNote(
            CustomerOrder order,
            SalesReturn salesReturn,
            BigDecimal amount,
            String settlementMethod,
            Long createdBy
    ) {
        CreditNote note = new CreditNote();
        note.setCreditNoteNo(generateCreditNoteNo(order.getBranchId()));
        note.setBranchId(order.getBranchId());
        note.setOrderId(order.getOrderId());
        note.setReturnId(salesReturn.getReturnId());
        note.setCustomerId(salesReturn.getCustomerId());
        note.setAmount(amount);
        note.setSettlementMethod(settlementMethod);
        if ("CUSTOMER_CREDIT".equals(settlementMethod) || "RETURN_VOUCHER".equals(settlementMethod) || "EXCHANGE".equals(settlementMethod)) {
            note.setUsedAmount(BigDecimal.ZERO);
            note.setRemainingAmount(amount);
            note.setStatus("ACTIVE");
        } else {
            note.setUsedAmount(amount);
            note.setRemainingAmount(BigDecimal.ZERO);
            note.setStatus("SETTLED");
        }
        note.setCreatedBy(createdBy);
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        return creditNoteRepository.save(note);
    }

    private ReturnVoucher createReturnVoucher(
            CustomerOrder order,
            SalesReturn salesReturn,
            CreditNote creditNote,
            BigDecimal amount,
            Long issuedBy
    ) {
        ReturnVoucher voucher = new ReturnVoucher();
        voucher.setBranchId(order.getBranchId());
        voucher.setVoucherNo(generateVoucherNo(order.getBranchId()));
        voucher.setRedemptionCode(generateVoucherToken());
        voucher.setOriginalOrderId(order.getOrderId());
        voucher.setSalesReturnId(salesReturn.getReturnId());
        voucher.setOriginalInvoiceNo(order.getInvoiceNo());
        voucher.setReturnNo(salesReturn.getReturnNo());
        voucher.setCreditNoteNo(creditNote.getCreditNoteNo());
        voucher.setOriginalAmount(amount);
        voucher.setUsedAmount(BigDecimal.ZERO);
        voucher.setRemainingAmount(amount);
        voucher.setStatus("ACTIVE");
        voucher.setIssueDate(LocalDate.now());
        voucher.setIssuedBy(issuedBy);
        voucher.setCreatedAt(LocalDateTime.now());
        voucher.setUpdatedAt(LocalDateTime.now());
        ReturnVoucher savedVoucher = returnVoucherRepository.save(voucher);

        recordVoucherTransaction(
                savedVoucher,
                "ISSUE",
                amount,
                amount,
                null,
                salesReturn.getReturnId(),
                salesReturn.getReturnNo(),
                "Voucher issued for return " + salesReturn.getReturnNo(),
                issuedBy
        );
        return savedVoucher;
    }

    private void refreshOrderReturnStatus(CustomerOrder order) {
        List<OrderProduct> lines = orderProductRepository.findByOrderId(order.getOrderId());
        BigDecimal soldQty = lines.stream()
                .map(line -> nvl(line.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnedQty = lines.stream()
                .map(line -> getPreviouslyReturnedQuantity(order.getOrderId(), line))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnedAmount = returnRepository.findByOrderId(order.getOrderId()).stream()
                .filter(ret -> !"CANCELLED".equalsIgnoreCase(ret.getStatus()))
                .filter(ret -> !"REJECTED".equalsIgnoreCase(ret.getStatus()))
                .map(ret -> nvl(ret.getRefundAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (returnedQty.compareTo(BigDecimal.ZERO) <= 0) {
            order.setReturnStatus("NONE");
        } else if (soldQty.compareTo(BigDecimal.ZERO) > 0 && returnedQty.compareTo(soldQty) >= 0) {
            order.setReturnStatus("FULLY_RETURNED");
        } else {
            order.setReturnStatus("PARTIALLY_RETURNED");
        }
        order.setReturnedAmount(returnedAmount.setScale(2, RoundingMode.HALF_UP));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    private void recordVoucherTransaction(
            ReturnVoucher voucher,
            String type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            Long orderId,
            Long returnId,
            String referenceNo,
            String note,
            Long createdBy
    ) {
        ReturnVoucherTransaction transaction = new ReturnVoucherTransaction();
        transaction.setVoucherId(voucher.getVoucherId());
        transaction.setVoucherNo(voucher.getVoucherNo());
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setOrderId(orderId);
        transaction.setSalesReturnId(returnId);
        transaction.setReferenceNo(referenceNo);
        transaction.setNote(note);
        transaction.setCreatedBy(createdBy);
        transaction.setCreatedAt(LocalDateTime.now());
        returnVoucherTransactionRepository.save(transaction);
    }

    private ReturnVoucherResponse mapReturnVoucher(ReturnVoucher voucher, boolean includeTransactions) {
        List<ReturnVoucherResponse.TransactionResponse> transactions = includeTransactions
                ? returnVoucherTransactionRepository.findByVoucherIdOrderByCreatedAtDesc(voucher.getVoucherId())
                        .stream()
                        .map(txn -> ReturnVoucherResponse.TransactionResponse.builder()
                                .transactionId(txn.getTransactionId())
                                .type(txn.getType())
                                .amount(txn.getAmount())
                                .balanceAfter(txn.getBalanceAfter())
                                .orderId(txn.getOrderId())
                                .salesReturnId(txn.getSalesReturnId())
                                .referenceNo(txn.getReferenceNo())
                                .note(txn.getNote())
                                .createdBy(txn.getCreatedBy())
                                .createdAt(txn.getCreatedAt())
                                .build())
                        .toList()
                : List.of();

        return ReturnVoucherResponse.builder()
                .voucherId(voucher.getVoucherId())
                .branchId(voucher.getBranchId())
                .voucherNo(voucher.getVoucherNo())
                .redemptionCode(voucher.getRedemptionCode())
                .originalOrderId(voucher.getOriginalOrderId())
                .salesReturnId(voucher.getSalesReturnId())
                .originalInvoiceNo(voucher.getOriginalInvoiceNo())
                .returnNo(voucher.getReturnNo())
                .creditNoteNo(voucher.getCreditNoteNo())
                .originalAmount(voucher.getOriginalAmount())
                .usedAmount(voucher.getUsedAmount())
                .remainingAmount(voucher.getRemainingAmount())
                .status(voucher.getStatus())
                .issueDate(voucher.getIssueDate())
                .expiryDate(voucher.getExpiryDate())
                .issuedBy(voucher.getIssuedBy())
                .createdAt(voucher.getCreatedAt())
                .transactions(transactions)
                .build();
    }

    private String normalizeVoucherCode(String code) {
        if (code == null || code.isBlank()) {
            throw new RuntimeException("Voucher number or redemption code is required");
        }
        return code.trim().toUpperCase();
    }

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
            if (!sameVariant(batch.getVariantId(), item.getVariantId())) {
                throw new RuntimeException("Batch does not belong to selected variant");
            }
            batch.setQtyRemaining(batch.getQtyRemaining().subtract(baseQty));
            batch.setAvailableQty(nvlQty(batch.getAvailableQty()).subtract(baseQty));
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

    private void addReturnStock(Long branchId, SalesReturnRequest.ReturnItemDto item, Long returnId, Long userId) {
        BigDecimal multiplier = itemUnitRepository
                .findByItemIdAndUnitIdAndIsActiveTrue(item.getItemId(), item.getUnitId())
                .map(ItemUnit::getMultiplierToBase)
                .orElse(BigDecimal.ONE);
        BigDecimal baseQty = item.getQuantity().multiply(multiplier);
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
                                batch.setQtyRemaining(batch.getQtyRemaining().add(baseQty));
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
        movement.setUnitId(item.getUnitId());
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
                .promotions(findSalePromotions(item, variantId, matchedBatch))
                .build();
    }

    private List<SaleProductSearchResponse.PromotionDto> findSalePromotions(Item item, Long variantId, StockBatch matchedBatch) {
        List<SaleProductSearchResponse.PromotionDto> result = new ArrayList<>();
        List<Promotion> activePromotions = promotionRepository.findActiveByBranchIdAndNow(item.getBranchId(), LocalDateTime.now());

        for (Promotion promotion : activePromotions) {
            promotionItemRepository.findByPromotionIdAndIsActiveTrue(promotion.getPromotionId()).stream()
                    .filter(promotionItem -> promotionItem.getItemId().equals(item.getItemId()))
                    .filter(promotionItem -> promotionItem.getVariantId() == null || promotionItem.getVariantId().equals(variantId))
                    .forEach(promotionItem -> result.add(mapSearchPromotion(promotion, promotionItem, null)));

            if (matchedBatch != null) {
                promotionBatchRepository.findByPromotionIdAndIsActiveTrue(promotion.getPromotionId()).stream()
                        .filter(promotionBatch -> batchPromotionMatches(promotionBatch, matchedBatch))
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
        target.setVoucherCode(source.getVoucherCode());
        target.setNote(source.getNote());
        return target;
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

    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new RuntimeException("Payment method is required");
        }
        return paymentMethod.trim().toUpperCase();
    }

    private ReturnVoucher redeemReturnVoucherForSale(
            String voucherNoOrCode,
            BigDecimal amount,
            CustomerOrder order,
            Long createdBy
    ) {
        String code = normalizeVoucherCode(voucherNoOrCode);
        ReturnVoucher voucher = returnVoucherRepository.findRedeemableByCodeForUpdate(code)
                .orElseThrow(() -> new RuntimeException("Return voucher not found"));

        if (!order.getBranchId().equals(voucher.getBranchId())) {
            throw new RuntimeException("Voucher does not belong to this branch");
        }
        if (!"ACTIVE".equalsIgnoreCase(voucher.getStatus())
                && !"PARTIALLY_USED".equalsIgnoreCase(voucher.getStatus())) {
            throw new RuntimeException("Voucher is not active");
        }
        if (voucher.getExpiryDate() != null && voucher.getExpiryDate().isBefore(LocalDate.now())) {
            voucher.setStatus("EXPIRED");
            voucher.setUpdatedAt(LocalDateTime.now());
            returnVoucherRepository.save(voucher);
            throw new RuntimeException("Voucher has expired");
        }
        if (nvl(voucher.getRemainingAmount()).compareTo(amount) < 0) {
            throw new RuntimeException("Voucher amount exceeds remaining voucher balance");
        }

        BigDecimal usedAmount = nvl(voucher.getUsedAmount()).add(amount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingAmount = nvl(voucher.getRemainingAmount()).subtract(amount).setScale(2, RoundingMode.HALF_UP);
        voucher.setUsedAmount(usedAmount);
        voucher.setRemainingAmount(remainingAmount);
        voucher.setStatus(remainingAmount.compareTo(BigDecimal.ZERO) <= 0 ? "REDEEMED" : "PARTIALLY_USED");
        voucher.setUpdatedAt(LocalDateTime.now());
        ReturnVoucher savedVoucher = returnVoucherRepository.save(voucher);

        recordVoucherTransaction(
                savedVoucher,
                "REDEEM",
                amount,
                remainingAmount,
                order.getOrderId(),
                null,
                order.getInvoiceNo(),
                "Voucher applied to sale " + order.getInvoiceNo(),
                createdBy
        );
        return savedVoucher;
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

    private String generateReturnNo(Long branchId) {
        String returnNo;
        do {
            returnNo = generateDocumentNo("RET", branchId);
        } while (returnRepository.existsByReturnNo(returnNo));
        return returnNo;
    }

    private String generateCreditNoteNo(Long branchId) {
        String creditNoteNo;
        do {
            creditNoteNo = generateDocumentNo("CN", branchId);
        } while (creditNoteRepository.existsByCreditNoteNo(creditNoteNo));
        return creditNoteNo;
    }

    private String generateVoucherNo(Long branchId) {
        String voucherNo;
        do {
            voucherNo = generateDocumentNo("RV", branchId);
        } while (returnVoucherRepository.existsByVoucherNo(voucherNo));
        return voucherNo;
    }

    private String generateVoucherToken() {
        String token;
        do {
            token = "RVC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        } while (returnVoucherRepository.existsByRedemptionCode(token));
        return token;
    }

    private String generateDocumentNo(String prefix, Long branchId) {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uniquePart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return prefix + "-" + branchId + "-" + datePart + "-" + uniquePart;
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

    private SalesReturn findReturnById(Long returnId) {
        return returnRepository.findById(returnId)
                .orElseThrow(() -> new RuntimeException("Return not found: " + returnId));
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // ─── Mappers ─────────────────────────────────────────────────────────────────

    private OrderResponse buildOrderResponse(CustomerOrder order) {
        List<OrderProductResponse> items = orderProductRepository.findByOrderId(order.getOrderId())
                .stream().map(this::mapOrderProduct).toList();

        List<PaymentResponse> payments = paymentRepository.findByOrderId(order.getOrderId())
                .stream().map(this::mapPayment).toList();

        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .branchId(order.getBranchId())
                .invoiceNo(order.getInvoiceNo())
                .orderNo(resolveOrderNo(order))
                .userId(order.getUserId())
                .customerId(order.getCustomerId())
                .customer(resolveCustomerResponse(order.getCustomerId()))
                .cashSessionId(order.getCashSessionId())
                .subtotal(order.getSubtotal())
                .discount(order.getDiscount())
                .taxAmount(order.getTaxAmount())
                .rounding(order.getRounding())
                .total(order.getTotal())
                .paymentStatus(order.getPaymentStatus())
                .status(order.getStatus())
                .returnStatus(order.getReturnStatus())
                .returnedAmount(order.getReturnedAmount())
                .orderDate(order.getOrderDate())
                .notes(order.getNotes())
                .items(items)
                .payments(payments)
                .build();
    }

    private CustomerResponse resolveCustomerResponse(Long customerId) {
        if (customerId == null) {
            return null;
        }

        return customerRepository.findById(customerId)
                .map(this::mapCustomerResponse)
                .orElse(null);
    }

    private CustomerResponse mapCustomerResponse(Customer customer) {
        CustomerResponse response = new CustomerResponse();
        response.setCustomerId(customer.getCustomerId());
        response.setBranchId(customer.getBranchId());
        response.setAuthId(customer.getAuthId());
        response.setName(customer.getName());
        response.setNic(customer.getNic());
        response.setPhone(customer.getPhone());
        response.setEmail(customer.getEmail());
        response.setAddress(customer.getAddress());
        response.setShopBalance(customer.getShopBalance());
        response.setLoyaltyCardNo(customer.getLoyaltyCardNo());
        response.setLoyaltyPoints(customer.getLoyaltyPoints());
        response.setCreditLimit(customer.getCreditLimit());
        response.setIsActive(customer.getIsActive());
        response.setCreatedAt(customer.getCreatedAt());
        response.setUpdatedAt(customer.getUpdatedAt());
        return response;
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
                .lineTotal(op.getLineTotal())
                .createdAt(op.getCreatedAt())
                .build();
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

    private SalesReturnResponse buildReturnResponse(SalesReturn r) {
        String invoiceNo = orderRepository.findById(r.getOrderId())
                .map(CustomerOrder::getInvoiceNo).orElse(null);

        List<SalesReturnResponse.ReturnItemResponse> items = returnItemRepository.findByReturnId(r.getReturnId())
                .stream().map(ri -> {
                    String itemName = itemRepository.findById(ri.getItemId())
                            .map(i -> i.getName()).orElse(null);
                    OrderProduct originalLine = ri.getOrderProductId() != null
                            ? orderProductRepository.findById(ri.getOrderProductId()).orElse(null)
                            : null;
                    ItemUnit unit = ri.getUnitId() != null ? itemUnitRepository.findById(ri.getUnitId()).orElse(null) : null;
                    return SalesReturnResponse.ReturnItemResponse.builder()
                            .returnItemId(ri.getReturnItemId())
                            .orderProductId(ri.getOrderProductId())
                            .itemId(ri.getItemId())
                            .variantId(ri.getVariantId())
                            .variantSku(resolveVariantSku(ri.getVariantId()))
                            .variantLabel(resolveVariantLabel(ri.getVariantId()))
                            .itemName(itemName)
                            .unitId(ri.getUnitId())
                            .unitName(resolveUnitName(unit))
                            .internalBatchBarcode(ri.getInternalBatchBarcode())
                            .quantity(ri.getQuantity())
                            .soldQuantity(originalLine != null ? originalLine.getQuantity() : null)
                            .previouslyReturnedQuantity(ri.getPreviouslyReturnedQuantity())
                            .returnableQuantity(ri.getReturnableQuantity())
                            .unitPrice(ri.getUnitPrice())
                            .lineRefund(ri.getLineRefund())
                            .condition(ri.getCondition())
                            .build();
                }).toList();

        ReturnVoucherResponse voucher = r.getVoucherNo() != null
                ? returnVoucherRepository.findByVoucherNo(r.getVoucherNo())
                        .map(v -> mapReturnVoucher(v, true))
                        .orElse(null)
                : null;

        return SalesReturnResponse.builder()
                .returnId(r.getReturnId())
                .returnNo(r.getReturnNo())
                .orderId(r.getOrderId())
                .invoiceNo(invoiceNo)
                .branchId(r.getBranchId())
                .customerId(r.getCustomerId())
                .cashSessionId(r.getCashSessionId())
                .returnDate(r.getReturnDate())
                .refundMethod(r.getRefundMethod())
                .settlementMethod(r.getSettlementMethod())
                .refundAmount(r.getRefundAmount())
                .creditNoteNo(r.getCreditNoteNo())
                .voucherNo(r.getVoucherNo())
                .voucher(voucher)
                .exchangeOrderId(r.getExchangeOrderId())
                .reason(r.getReason())
                .processedBy(r.getProcessedBy())
                .approvedBy(r.getApprovedBy())
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
