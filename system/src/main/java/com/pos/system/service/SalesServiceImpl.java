package com.pos.system.service;

import com.pos.system.dto.sale.*;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.ScaleBarcodeSetting;
import com.pos.system.model.catalog.ScaleItemMapping;
import com.pos.system.model.cash.CashSessionTransaction;
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
    private final ItemRepository itemRepository;
    private final ScaleBarcodeSettingRepository scaleBarcodeSettingRepository;
    private final ScaleItemMappingRepository scaleItemMappingRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionItemRepository promotionItemRepository;
    private final PromotionBatchRepository promotionBatchRepository;
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
            BigDecimal lineDiscount = nvl(item.getDiscount());
            BigDecimal lineTotal = item.getUnitPrice()
                    .multiply(item.getQuantity())
                    .subtract(lineDiscount)
                    .setScale(2, RoundingMode.HALF_UP);

            OrderProduct op = new OrderProduct();
            op.setOrderId(savedOrder.getOrderId());
            op.setItemId(item.getItemId());
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
                        unit,
                        null,
                        null
                )));

        decodeScaleBarcode(branchId, normalized).ifPresent(result -> addResult(results, result));

        stockBatchRepository.searchByBranchAndBatchText(branchId, normalized)
                .forEach(batch -> addResult(results, buildSaleSearchResult(
                        "STOCK_BATCH",
                        findActiveItem(branchId, batch.getItemId()),
                        findUnit(batch.getItemId(), batch.getUnitId()).orElse(null),
                        batch,
                        null
                )));

        itemRepository.searchActiveByBranchAndNameOrSku(branchId, normalized)
                .forEach(item -> addResult(results, buildSaleSearchResult(
                        "ITEM",
                        item,
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

        BigDecimal totalPaid = BigDecimal.ZERO;
        for (PaymentRequest.PaymentLineDto line : request.getPayments()) {
            Payment payment = new Payment();
            payment.setBranchId(order.getBranchId());
            payment.setOrderId(orderId);
            payment.setCustomerId(order.getCustomerId());
            payment.setCashSessionId(order.getCashSessionId());
            payment.setAmount(line.getAmount());
            payment.setTenderedAmount(line.getTenderedAmount());
            payment.setChangeAmount(
                    line.getTenderedAmount() != null
                            ? line.getTenderedAmount().subtract(line.getAmount()).max(BigDecimal.ZERO)
                            : BigDecimal.ZERO
            );
            payment.setPaymentMethod(line.getPaymentMethod());
            payment.setPaymentDate(LocalDateTime.now());
            payment.setReceivedBy(request.getReceivedBy());
            payment.setReferenceNo(line.getReferenceNo());
            payment.setNote(line.getNote());
            Payment savedPayment = paymentRepository.save(payment);

            totalPaid = totalPaid.add(line.getAmount());

            if ("CREDIT".equalsIgnoreCase(line.getPaymentMethod())) {
                if (order.getCustomerId() == null) {
                    throw new RuntimeException("Customer is required for credit payments");
                }
                customerService.recordCreditSale(
                        order.getCustomerId(),
                        line.getAmount(),
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
                txn.setAmount(line.getAmount());
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
        BigDecimal alreadyPaid = paymentRepository.findByOrderId(orderId).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (alreadyPaid.compareTo(nvl(order.getTotal())) >= 0) {
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
        CustomerOrder order = findOrderById(request.getOrderId());

        if ("CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("Cannot return items from a cancelled order");
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

        for (SalesReturnRequest.ReturnItemDto item : request.getItems()) {
            BigDecimal lineRefund = item.getUnitPrice()
                    .multiply(item.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP);
            totalRefund = totalRefund.add(lineRefund);

            SalesReturnItem returnItem = new SalesReturnItem();
            returnItem.setReturnId(savedReturn.getReturnId());
            returnItem.setItemId(item.getItemId());
            returnItem.setInternalBatchBarcode(item.getInternalBatchBarcode());
            returnItem.setQuantity(item.getQuantity());
            returnItem.setUnitPrice(item.getUnitPrice());
            returnItem.setLineRefund(lineRefund);
            returnItem.setCondition(item.getCondition());
            returnItemRepository.save(returnItem);

            // add stock back
            addReturnStock(request.getBranchId(), item, savedReturn.getReturnId(), request.getProcessedBy());
        }

        savedReturn.setRefundAmount(totalRefund);
        returnRepository.save(savedReturn);

        // write cash session transaction for cash refund
        if ("CASH".equals(request.getRefundMethod()) && order.getCashSessionId() != null) {
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
    public List<SalesReturnResponse> getReturnsByOrder(Long orderId) {
        return returnRepository.findByOrderId(orderId)
                .stream().map(this::buildReturnResponse).toList();
    }

    @Override
    public List<SalesReturnResponse> getReturnsByBranch(Long branchId) {
        return returnRepository.findByBranchIdOrderByReturnDateDesc(branchId)
                .stream().map(this::buildReturnResponse).toList();
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
        Stock stock = stockRepository.findByBranchIdAndItemId(branchId, item.getItemId())
                .orElseThrow(() -> new RuntimeException("No stock record for item: " + item.getItemId()));

        BigDecimal availableQty = getBatchQtyTotal(branchId, item.getItemId(), StockQtyType.AVAILABLE);
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
            batch.setQtyRemaining(batch.getQtyRemaining().subtract(baseQty));
            batch.setAvailableQty(nvlQty(batch.getAvailableQty()).subtract(baseQty));
            stockBatchRepository.save(batch);
        } else {
            // FIFO — deduct from oldest batches first
            deductFIFO(branchId, item.getItemId(), baseQty);
        }

        // record stock movement
        StockMovement movement = new StockMovement();
        movement.setBranchId(branchId);
        movement.setMovementType("SALE");
        movement.setItemId(item.getItemId());
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

    private void deductFIFO(Long branchId, Long itemId, BigDecimal baseQtyToDeduct) {
        List<StockBatch> batches = stockBatchRepository
                .findByBranchIdAndItemIdOrderByCreatedAtDesc(branchId, itemId);

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

        stockRepository.findByBranchIdAndItemId(branchId, op.getItemId()).ifPresent(stock -> {
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
        // only add back to stock if condition is GOOD
        if (!"GOOD".equalsIgnoreCase(item.getCondition())) {
            // damaged/expired — move to damaged stock
            stockRepository.findByBranchIdAndItemId(branchId, item.getItemId()).ifPresent(stock -> {
                stock.setLastUpdated(LocalDateTime.now());
                stockRepository.save(stock);
            });
            if (item.getInternalBatchBarcode() != null) {
                stockBatchRepository.findByBranchIdAndInternalBatchBarcode(branchId, item.getInternalBatchBarcode())
                        .ifPresent(batch -> {
                            if ("EXPIRED".equalsIgnoreCase(item.getCondition())) {
                                batch.setExpiredQty(nvlQty(batch.getExpiredQty()).add(item.getQuantity()));
                            } else {
                                batch.setDamagedQty(nvlQty(batch.getDamagedQty()).add(item.getQuantity()));
                            }
                            stockBatchRepository.save(batch);
                        });
            }
        } else {
            stockRepository.findByBranchIdAndItemId(branchId, item.getItemId()).ifPresent(stock -> {
                stock.setLastUpdated(LocalDateTime.now());
                stockRepository.save(stock);

                if (item.getInternalBatchBarcode() != null) {
                    stockBatchRepository.findByBranchIdAndInternalBatchBarcode(branchId, item.getInternalBatchBarcode())
                            .ifPresent(batch -> {
                                batch.setQtyRemaining(batch.getQtyRemaining().add(item.getQuantity()));
                                batch.setAvailableQty(nvlQty(batch.getAvailableQty()).add(item.getQuantity()));
                                stockBatchRepository.save(batch);
                            });
                }
            });
        }

        StockMovement movement = new StockMovement();
        movement.setBranchId(branchId);
        movement.setMovementType("SALE_RETURN");
        movement.setItemId(item.getItemId());
        movement.setUnitId(1L); // base unit for returns
        movement.setInternalBatchBarcode(item.getInternalBatchBarcode());
        movement.setQuantity(item.getQuantity());
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
                .quantityUnit(unit.getUnitName())
                .encodedPrice(encodedPrice)
                .build();

        return Optional.of(buildSaleSearchResult("SCALE_BARCODE", item, unit, null, scan));
    }

    private SaleProductSearchResponse buildSaleSearchResult(String matchType, Item item, ItemUnit matchedUnit,
                                                            StockBatch matchedBatch, SaleProductSearchResponse.ScanDto scan) {
        List<ItemUnit> units = itemUnitRepository.findByItemIdAndIsActiveTrue(item.getItemId());
        List<StockBatch> batches = stockBatchRepository.findByBranchIdAndItemIdOrderByCreatedAtDesc(item.getBranchId(), item.getItemId());

        return SaleProductSearchResponse.builder()
                .matchType(matchType)
                .scan(scan)
                .item(mapSearchItem(item))
                .matchedUnit(matchedUnit != null ? mapSearchUnit(matchedUnit) : null)
                .matchedBatch(matchedBatch != null ? mapSearchBatch(matchedBatch) : null)
                .stock(stockRepository.findByBranchIdAndItemId(item.getBranchId(), item.getItemId()).map(this::mapSearchStock).orElse(null))
                .units(units.stream().map(this::mapSearchUnit).toList())
                .batches(batches.stream().map(this::mapSearchBatch).toList())
                .promotions(findSalePromotions(item, matchedBatch))
                .build();
    }

    private List<SaleProductSearchResponse.PromotionDto> findSalePromotions(Item item, StockBatch matchedBatch) {
        List<SaleProductSearchResponse.PromotionDto> result = new ArrayList<>();
        List<Promotion> activePromotions = promotionRepository.findActiveByBranchIdAndNow(item.getBranchId(), LocalDateTime.now());

        for (Promotion promotion : activePromotions) {
            promotionItemRepository.findByPromotionIdAndIsActiveTrue(promotion.getPromotionId()).stream()
                    .filter(promotionItem -> promotionItem.getItemId().equals(item.getItemId()))
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
                .unitName(unit.getUnitName())
                .multiplierToBase(unit.getMultiplierToBase())
                .barcode(unit.getBarcode())
                .defaultSellingPrice(unit.getDefaultSellingPrice())
                .isBaseUnit(unit.getIsBaseUnit())
                .isActive(unit.getIsActive())
                .build();
    }

    private SaleProductSearchResponse.StockDto mapSearchStock(Stock stock) {
        return SaleProductSearchResponse.StockDto.builder()
                .stockId(stock.getStockId())
                .unitId(stock.getUnitId())
                .lastUpdated(stock.getLastUpdated())
                .build();
    }

    private SaleProductSearchResponse.BatchDto mapSearchBatch(StockBatch batch) {
        ItemUnit unit = findUnit(batch.getItemId(), batch.getUnitId()).orElse(null);
        return SaleProductSearchResponse.BatchDto.builder()
                .stockBatchId(batch.getStockBatchId())
                .supplyProductId(batch.getSupplyProductId())
                .unitId(batch.getUnitId())
                .unitName(unit != null ? unit.getUnitName() : null)
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
        results.putIfAbsent(response.getItem().getItemId() + batchKey, response);
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
                .userId(order.getUserId())
                .customerId(order.getCustomerId())
                .cashSessionId(order.getCashSessionId())
                .subtotal(order.getSubtotal())
                .discount(order.getDiscount())
                .taxAmount(order.getTaxAmount())
                .rounding(order.getRounding())
                .total(order.getTotal())
                .paymentStatus(order.getPaymentStatus())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .notes(order.getNotes())
                .items(items)
                .payments(payments)
                .build();
    }

    private OrderProductResponse mapOrderProduct(OrderProduct op) {
        String itemName = itemRepository.findById(op.getItemId())
                .map(i -> i.getName()).orElse(null);
        String unitName = itemUnitRepository.findByUnitIdAndIsActiveTrue(op.getUnitId())
                .map(u -> u.getUnitName()).orElse(null);

        return OrderProductResponse.builder()
                .orderProductId(op.getOrderProductId())
                .orderId(op.getOrderId())
                .itemId(op.getItemId())
                .itemName(itemName)
                .unitId(op.getUnitId())
                .unitName(unitName)
                .batchBarcode(op.getBatchBarcode())
                .quantity(op.getQuantity())
                .unitPrice(op.getUnitPrice())
                .discount(op.getDiscount())
                .lineTotal(op.getLineTotal())
                .createdAt(op.getCreatedAt())
                .build();
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
                    return SalesReturnResponse.ReturnItemResponse.builder()
                            .returnItemId(ri.getReturnItemId())
                            .itemId(ri.getItemId())
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
                .reason(r.getReason())
                .processedBy(r.getProcessedBy())
                .status(r.getStatus())
                .items(items)
                .build();
    }

    private BigDecimal nvlQty(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal getBatchQtyTotal(Long branchId, Long itemId, StockQtyType type) {
        return stockBatchRepository.findByBranchIdAndItemId(branchId, itemId)
                .stream()
                .map(batch -> switch (type) {
                    case AVAILABLE -> nvlQty(batch.getAvailableQty() != null ? batch.getAvailableQty() : batch.getQtyRemaining());
                    case DAMAGED -> nvlQty(batch.getDamagedQty());
                    case EXPIRED -> nvlQty(batch.getExpiredQty());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private enum StockQtyType {
        AVAILABLE,
        DAMAGED,
        EXPIRED
    }
}
