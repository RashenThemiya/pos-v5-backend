package com.pos.system.service;

import com.pos.system.dto.sale.PaymentRequest;
import com.pos.system.dto.sale.SalesReturnRequest;
import com.pos.system.dto.sale.SalesReturnResponse;
import com.pos.system.dto.sale.CustomerOrderViewResponse;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.ItemVariant;
import com.pos.system.model.catalog.ItemVariantAttribute;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.model.sale.OrderProduct;
import com.pos.system.model.sale.Payment;
import com.pos.system.model.sale.SalesReturn;
import com.pos.system.model.sale.SalesReturnItem;
import com.pos.system.model.cash.CashSessionTransaction;
import com.pos.system.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesServiceImplReturnTest {

    @Mock private CustomerOrderRepository orderRepository;
    @Mock private OrderProductRepository orderProductRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderStatusHistoryRepository statusHistoryRepository;
    @Mock private SalesReturnRepository returnRepository;
    @Mock private SalesReturnItemRepository returnItemRepository;
    @Mock private StockRepository stockRepository;
    @Mock private StockBatchRepository stockBatchRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private CashSessionTransactionRepository cashSessionTransactionRepository;
    @Mock private ItemUnitRepository itemUnitRepository;
    @Mock private UnitMasterRepository unitMasterRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private ItemVariantRepository itemVariantRepository;
    @Mock private ItemVariantAttributeRepository itemVariantAttributeRepository;
    @Mock private ScaleBarcodeSettingRepository scaleBarcodeSettingRepository;
    @Mock private ScaleItemMappingRepository scaleItemMappingRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private PromotionItemRepository promotionItemRepository;
    @Mock private PromotionBatchRepository promotionBatchRepository;
    @Mock private CustomerService customerService;
    @Mock private SalePromotionService salePromotionService;

    private SalesServiceImpl salesService;

    @BeforeEach
    void setUp() {
        salesService = new SalesServiceImpl(
                orderRepository,
                orderProductRepository,
                paymentRepository,
                statusHistoryRepository,
                returnRepository,
                returnItemRepository,
                stockRepository,
                stockBatchRepository,
                stockMovementRepository,
                cashSessionTransactionRepository,
                itemUnitRepository,
                unitMasterRepository,
                itemRepository,
                itemVariantRepository,
                itemVariantAttributeRepository,
                scaleBarcodeSettingRepository,
                scaleItemMappingRepository,
                promotionRepository,
                promotionItemRepository,
                promotionBatchRepository,
                customerService,
                salePromotionService
        );
    }

    private CustomerOrder completedOrder() {
        CustomerOrder order = new CustomerOrder();
        order.setOrderId(1L);
        order.setBranchId(2L);
        order.setInvoiceNo("INV-0001");
        order.setCashSessionId(10L);
        order.setStatus("COMPLETED");
        return order;
    }

    @Test
    void processSaleForwardsSelectedBillPromotionsToOrderCreation() {
        SalesServiceImpl spyService = spy(salesService);
        com.pos.system.dto.sale.ProcessSaleRequest request = new com.pos.system.dto.sale.ProcessSaleRequest();
        request.setBranchId(2L);
        request.setBillPromotionIds(List.of(7L, 8L));
        request.setDiscount(new BigDecimal("190"));
        com.pos.system.dto.sale.OrderResponse response = new com.pos.system.dto.sale.OrderResponse();
        doReturn(response).when(spyService).createOrder(any());
        assertThat(spyService.processSale(request)).isSameAs(response);
        ArgumentCaptor<com.pos.system.dto.sale.CreateOrderRequest> captor =
                ArgumentCaptor.forClass(com.pos.system.dto.sale.CreateOrderRequest.class);
        verify(spyService).createOrder(captor.capture());
        assertThat(captor.getValue().getBillPromotionIds()).containsExactly(7L, 8L);
        assertThat(captor.getValue().getDiscount()).isEqualByComparingTo("190");
    }

    private OrderProduct soldLine(long orderProductId, long itemId, String qty, String unitPrice, String discount) {
        OrderProduct line = new OrderProduct();
        line.setOrderProductId(orderProductId);
        line.setOrderId(1L);
        line.setItemId(itemId);
        line.setUnitId(3L);
        line.setQuantity(new BigDecimal(qty));
        line.setUnitPrice(new BigDecimal(unitPrice));
        line.setDiscount(new BigDecimal(discount));
        line.setLineTotal(new BigDecimal(unitPrice).multiply(new BigDecimal(qty)).subtract(new BigDecimal(discount)));
        return line;
    }

    private SalesReturnItem priorReturnItem(Long orderProductId, long itemId, String qty, String refund) {
        SalesReturnItem item = new SalesReturnItem();
        item.setReturnId(50L);
        item.setOrderProductId(orderProductId);
        item.setItemId(itemId);
        item.setQuantity(new BigDecimal(qty));
        item.setLineRefund(new BigDecimal(refund));
        return item;
    }

    private Payment payment(String amount) {
        Payment payment = new Payment();
        payment.setOrderId(1L);
        payment.setPaymentMethod("CASH");
        payment.setAmount(new BigDecimal(amount));
        return payment;
    }

    /** Stubs an order that was paid in full. */
    private void stubOrder(CustomerOrder order, List<OrderProduct> lines, List<SalesReturnItem> priorItems) {
        lenient().when(paymentRepository.findByOrderId(1L))
                .thenReturn(order.getTotal() == null ? List.of() : List.of(payment(order.getTotal().toPlainString())));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderProductRepository.findByOrderId(1L)).thenReturn(lines);
        if (!priorItems.isEmpty()) {
            SalesReturn prior = new SalesReturn();
            prior.setReturnId(50L);
            prior.setStatus("COMPLETED");
            prior.setRefundAmount(priorItems.stream().map(SalesReturnItem::getLineRefund)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            when(returnRepository.findByOrderId(1L)).thenReturn(List.of(prior));
            when(returnItemRepository.findByReturnIdIn(List.of(50L))).thenReturn(priorItems);
        }
    }

    private void stubReturnSaves(long returnId) {
        when(returnRepository.save(any(SalesReturn.class))).thenAnswer(inv -> {
            SalesReturn r = inv.getArgument(0);
            if (r.getReturnId() == null) r.setReturnId(returnId);
            return r;
        });
        when(returnItemRepository.save(any(SalesReturnItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private SalesReturnRequest cashReturn(SalesReturnRequest.ReturnItemDto... items) {
        SalesReturnRequest request = new SalesReturnRequest();
        request.setOrderId(1L);
        request.setBranchId(2L);
        request.setProcessedBy(7L);
        request.setRefundMethod("CASH");
        request.setItems(List.of(items));
        return request;
    }

    private SalesReturnRequest.ReturnItemDto returnItem(long itemId, String qty, String price, String condition) {
        SalesReturnRequest.ReturnItemDto item = new SalesReturnRequest.ReturnItemDto();
        item.setItemId(itemId);
        item.setQuantity(new BigDecimal(qty));
        item.setUnitPrice(new BigDecimal(price));
        item.setCondition(condition);
        return item;
    }

    @Test
    void createReturn_withCashRefund_writesCashSessionTransactionAgainstOrderSession() {
        CustomerOrder order = completedOrder();
        order.setSubtotal(new BigDecimal("300.00"));
        order.setTotal(new BigDecimal("300.00"));
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "150.00", "0")), List.of());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(returnRepository.save(any(SalesReturn.class))).thenAnswer(inv -> {
            SalesReturn r = inv.getArgument(0);
            if (r.getReturnId() == null) r.setReturnId(99L);
            return r;
        });
        when(returnItemRepository.save(any(SalesReturnItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(returnItemRepository.findByReturnId(99L)).thenReturn(List.of());

        SalesReturnRequest request = new SalesReturnRequest();
        request.setOrderId(1L);
        request.setBranchId(2L);
        request.setCustomerId(5L);
        request.setProcessedBy(7L);
        request.setRefundMethod("CASH");
        request.setReason("Wrong size");
        request.setItems(List.of(returnItem(100L, "2", "150.00", "GOOD")));

        SalesReturnResponse response = salesService.createReturn(request);

        assertThat(response.getRefundAmount()).isEqualByComparingTo("300.00");
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getInvoiceNo()).isEqualTo("INV-0001");

        ArgumentCaptor<CashSessionTransaction> txnCaptor = ArgumentCaptor.forClass(CashSessionTransaction.class);
        verify(cashSessionTransactionRepository).save(txnCaptor.capture());
        CashSessionTransaction txn = txnCaptor.getValue();
        assertThat(txn.getSessionId()).isEqualTo(10L);
        assertThat(txn.getType()).isEqualTo("REFUND");
        assertThat(txn.getAmount()).isEqualByComparingTo("300.00");
        assertThat(txn.getOrderId()).isEqualTo(1L);
        assertThat(txn.getInvoiceNo()).isEqualTo("INV-0001");
        assertThat(txn.getSalesReturnId()).isEqualTo(99L);
    }

    @Test
    void createReturn_withCardRefund_doesNotTouchCashDrawer() {
        CustomerOrder order = completedOrder();
        order.setTotal(new BigDecimal("50.00"));
        stubOrder(order, List.of(soldLine(11L, 100L, "1", "50.00", "0")), List.of());
        when(returnRepository.save(any(SalesReturn.class))).thenAnswer(inv -> {
            SalesReturn r = inv.getArgument(0);
            if (r.getReturnId() == null) r.setReturnId(101L);
            return r;
        });
        when(returnItemRepository.save(any(SalesReturnItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(returnItemRepository.findByReturnId(101L)).thenReturn(List.of());

        SalesReturnRequest request = new SalesReturnRequest();
        request.setOrderId(1L);
        request.setBranchId(2L);
        request.setProcessedBy(7L);
        request.setRefundMethod("CARD");
        request.setItems(List.of(returnItem(100L, "1", "50.00", "GOOD")));

        salesService.createReturn(request);

        verifyNoInteractions(cashSessionTransactionRepository);
    }

    @Test
    void processPayment_writesCashSessionTransactionWithOrderAndInvoiceLinked() {
        CustomerOrder order = completedOrder();
        order.setPaymentStatus("UNPAID");
        order.setTotal(new BigDecimal("300.00"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getPaymentId() == null) p.setPaymentId(55L);
            return p;
        });
        when(paymentRepository.findByOrderId(1L)).thenReturn(List.of());
        when(orderProductRepository.findByOrderId(1L)).thenReturn(List.of());

        PaymentRequest.PaymentLineDto line = new PaymentRequest.PaymentLineDto();
        line.setPaymentMethod("CASH");
        line.setAmount(new BigDecimal("300.00"));
        line.setTenderedAmount(new BigDecimal("300.00"));

        PaymentRequest request = new PaymentRequest();
        request.setReceivedBy(7L);
        request.setPayments(List.of(line));

        salesService.processPayment(1L, request);

        ArgumentCaptor<CashSessionTransaction> txnCaptor = ArgumentCaptor.forClass(CashSessionTransaction.class);
        verify(cashSessionTransactionRepository).save(txnCaptor.capture());
        CashSessionTransaction txn = txnCaptor.getValue();
        assertThat(txn.getType()).isEqualTo("SALE");
        assertThat(txn.getAmount()).isEqualByComparingTo("300.00");
        assertThat(txn.getOrderId()).isEqualTo(1L);
        assertThat(txn.getInvoiceNo()).isEqualTo("INV-0001");
        assertThat(txn.getPaymentId()).isEqualTo(55L);
    }

    @Test
    void processPayment_withOverTenderedCash_recordsInvoiceBalanceAsRevenue() {
        CustomerOrder order = completedOrder();
        order.setPaymentStatus("UNPAID");
        order.setTotal(new BigDecimal("300.00"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getPaymentId() == null) p.setPaymentId(57L);
            return p;
        });
        when(paymentRepository.findByOrderId(1L)).thenReturn(List.of());
        when(orderProductRepository.findByOrderId(1L)).thenReturn(List.of());

        PaymentRequest.PaymentLineDto line = new PaymentRequest.PaymentLineDto();
        line.setPaymentMethod("CASH");
        line.setAmount(new BigDecimal("500.00"));
        line.setTenderedAmount(new BigDecimal("500.00"));

        PaymentRequest request = new PaymentRequest();
        request.setReceivedBy(7L);
        request.setPayments(List.of(line));

        salesService.processPayment(1L, request);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment payment = paymentCaptor.getValue();
        assertThat(payment.getAmount()).isEqualByComparingTo("300.00");
        assertThat(payment.getTenderedAmount()).isEqualByComparingTo("500.00");
        assertThat(payment.getChangeAmount()).isEqualByComparingTo("200.00");

        ArgumentCaptor<CashSessionTransaction> txnCaptor = ArgumentCaptor.forClass(CashSessionTransaction.class);
        verify(cashSessionTransactionRepository).save(txnCaptor.capture());
        assertThat(txnCaptor.getValue().getAmount()).isEqualByComparingTo("300.00");
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void processPayment_withCreditPayment_deductsCustomerBalance() {
        CustomerOrder order = completedOrder();
        order.setCustomerId(5L);
        order.setPaymentStatus("UNPAID");
        order.setTotal(new BigDecimal("125.00"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getPaymentId() == null) p.setPaymentId(56L);
            return p;
        });
        when(paymentRepository.findByOrderId(1L)).thenReturn(List.of());
        when(orderProductRepository.findByOrderId(1L)).thenReturn(List.of());

        PaymentRequest.PaymentLineDto line = new PaymentRequest.PaymentLineDto();
        line.setPaymentMethod("CREDIT");
        line.setAmount(new BigDecimal("125.00"));

        PaymentRequest request = new PaymentRequest();
        request.setReceivedBy(7L);
        request.setPayments(List.of(line));

        salesService.processPayment(1L, request);

        verify(customerService).recordCreditSale(
                5L,
                new BigDecimal("125.00"),
                1L,
                "INV-0001",
                7L
        );
    }

    @Test
    void getCustomerOrdersByBranch_returnsOrderViewDetails() {
        CustomerOrder order = completedOrder();
        order.setCustomerId(5L);
        order.setOrderNo("SO-001");
        order.setTotal(new BigDecimal("200.00"));

        OrderProduct product = new OrderProduct();
        product.setOrderId(1L);
        product.setItemId(10L);
        product.setVariantId(12L);
        product.setUnitId(3L);
        product.setQuantity(new BigDecimal("2"));
        product.setUnitPrice(new BigDecimal("100.00"));
        product.setLineTotal(new BigDecimal("200.00"));

        Item item = new Item();
        item.setItemId(10L);
        item.setName("Coca-Cola 500ml");

        ItemUnit unit = new ItemUnit();
        unit.setUnitId(3L);
        unit.setUnitName("PCS");

        ItemVariant variant = new ItemVariant();
        variant.setVariantId(12L);
        variant.setSku("COKE-500-CHILLED");

        ItemVariantAttribute variantAttribute = new ItemVariantAttribute();
        variantAttribute.setAttributeName("Bottle");
        variantAttribute.setAttributeValue("500ml");

        Payment payment = new Payment();
        payment.setOrderId(1L);
        payment.setPaymentMethod("CASH");

        PageRequest pageRequest = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "orderDate"));

        when(orderRepository.findByBranchIdAndCustomerId(2L, 5L, pageRequest))
                .thenReturn(new PageImpl<>(List.of(order), pageRequest, 1));
        when(orderProductRepository.findByOrderId(1L)).thenReturn(List.of(product));
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(itemUnitRepository.findById(3L)).thenReturn(Optional.of(unit));
        when(itemVariantRepository.findById(12L)).thenReturn(Optional.of(variant));
        when(itemVariantAttributeRepository.findByVariantIdOrderByAttributeNameAsc(12L))
                .thenReturn(List.of(variantAttribute));
        when(paymentRepository.findByOrderId(1L)).thenReturn(List.of(payment));

        var page = salesService.getCustomerOrdersByBranch(2L, 5L, pageRequest);
        List<CustomerOrderViewResponse> responses = page.getContent();

        assertThat(responses).hasSize(1);
        assertThat(page.getPage()).isEqualTo(0);
        assertThat(page.getPageSize()).isEqualTo(25);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.getSort()).isEqualTo("orderDate,desc");
        CustomerOrderViewResponse response = responses.get(0);
        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getInvoiceNo()).isEqualTo("INV-0001");
        assertThat(response.getOrderNo()).isEqualTo("SO-001");
        assertThat(response.getPaymentMethod()).isEqualTo("CASH");
        assertThat(response.getItemCount()).isEqualByComparingTo("2");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("200.00");
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getItemId()).isEqualTo(10L);
        assertThat(response.getItems().get(0).getVariantId()).isEqualTo(12L);
        assertThat(response.getItems().get(0).getVariantSku()).isEqualTo("COKE-500-CHILLED");
        assertThat(response.getItems().get(0).getVariantLabel()).isEqualTo("Bottle: 500ml");
        assertThat(response.getItems().get(0).getItemName()).isEqualTo("Coca-Cola 500ml");
        assertThat(response.getItems().get(0).getUnitName()).isEqualTo("PCS");
        assertThat(response.getItems().get(0).getQuantity()).isEqualByComparingTo("2");
        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(response.getItems().get(0).getLineTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void createReturn_rejectsCancelledOrder() {
        CustomerOrder order = completedOrder();
        order.setStatus("CANCELLED");
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        SalesReturnRequest request = new SalesReturnRequest();
        request.setOrderId(1L);
        request.setItems(List.of(returnItem(100L, "1", "50.00", "GOOD")));

        assertThrows(RuntimeException.class, () -> salesService.createReturn(request));
        verifyNoInteractions(returnRepository);
    }

    @Test
    void createReturn_refundsNetPaidAfterLinePromotion_notSubmittedPrice() {
        // 2 x 100 with a 20 line promotion: the customer paid 180, so a full return refunds 180, not 200.
        CustomerOrder order = completedOrder();
        order.setSubtotal(new BigDecimal("180.00"));
        order.setTotal(new BigDecimal("180.00"));
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "100.00", "20.00")), List.of());
        stubReturnSaves(99L);

        SalesReturnResponse response = salesService.createReturn(cashReturn(returnItem(100L, "2", "100.00", "GOOD")));

        assertThat(response.getRefundAmount()).isEqualByComparingTo("180.00");
        ArgumentCaptor<SalesReturnItem> itemCaptor = ArgumentCaptor.forClass(SalesReturnItem.class);
        verify(returnItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getOrderProductId()).isEqualTo(11L);
        assertThat(itemCaptor.getValue().getUnitPrice()).isEqualByComparingTo("90.00");
        ArgumentCaptor<CashSessionTransaction> txn = ArgumentCaptor.forClass(CashSessionTransaction.class);
        verify(cashSessionTransactionRepository).save(txn.capture());
        assertThat(txn.getValue().getAmount()).isEqualByComparingTo("180.00");
    }

    @Test
    void createReturn_partialReturnsOfPromotionalLineSumToNetPaid() {
        CustomerOrder order = completedOrder();
        order.setTotal(new BigDecimal("180.00"));
        // first unit already returned at the net price of 90
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "100.00", "20.00")),
                List.of(priorReturnItem(11L, 100L, "1", "90.00")));
        stubReturnSaves(99L);

        SalesReturnResponse response = salesService.createReturn(cashReturn(returnItem(100L, "1", "100.00", "GOOD")));

        assertThat(response.getRefundAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void createReturn_allocatesBillDiscountAcrossMixedSale() {
        // lines 400 + 480 = 880, bill discount 18 -> paid 862. Returning everything refunds 862.
        CustomerOrder order = completedOrder();
        order.setSubtotal(new BigDecimal("880.00"));
        order.setDiscount(new BigDecimal("18.00"));
        order.setTotal(new BigDecimal("862.00"));
        stubOrder(order, List.of(
                soldLine(11L, 100L, "4", "100.00", "0"),
                soldLine(12L, 200L, "2", "250.00", "20.00")), List.of());
        stubReturnSaves(99L);

        SalesReturnResponse response = salesService.createReturn(cashReturn(
                returnItem(100L, "4", "100.00", "GOOD"),
                returnItem(200L, "2", "250.00", "GOOD")));

        assertThat(response.getRefundAmount()).isEqualByComparingTo("862.00");
    }

    @Test
    void createReturn_rejectsQuantityBeyondWhatRemainsAfterPriorReturns() {
        // order 102: 2 sold, 2 already returned -> a further unit is refused before any write.
        CustomerOrder order = completedOrder();
        order.setTotal(new BigDecimal("180.00"));
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "100.00", "20.00")),
                List.of(priorReturnItem(11L, 100L, "2", "180.00")));

        assertThrows(RuntimeException.class,
                () -> salesService.createReturn(cashReturn(returnItem(100L, "1", "100.00", "GOOD"))));

        verify(returnRepository, never()).save(any());
        verify(returnItemRepository, never()).save(any());
        verifyNoInteractions(cashSessionTransactionRepository, stockMovementRepository, stockBatchRepository);
    }

    @Test
    void createReturn_countsLegacyReturnRowsWithoutOrderLineLink() {
        CustomerOrder order = completedOrder();
        order.setTotal(new BigDecimal("180.00"));
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "100.00", "20.00")),
                List.of(priorReturnItem(null, 100L, "2", "200.00")));

        assertThrows(RuntimeException.class,
                () -> salesService.createReturn(cashReturn(returnItem(100L, "1", "100.00", "GOOD"))));
        verify(returnRepository, never()).save(any());
    }

    @Test
    void createReturn_rejectsDuplicatedLinesInOneRequestThatExceedSoldQuantity() {
        CustomerOrder order = completedOrder();
        order.setTotal(new BigDecimal("180.00"));
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "100.00", "20.00")), List.of());

        assertThrows(RuntimeException.class, () -> salesService.createReturn(cashReturn(
                returnItem(100L, "2", "100.00", "GOOD"),
                returnItem(100L, "1", "100.00", "GOOD"))));
        verify(returnRepository, never()).save(any());
    }

    @Test
    void createReturn_rejectsItemNotOnOrder() {
        CustomerOrder order = completedOrder();
        order.setTotal(new BigDecimal("180.00"));
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "100.00", "20.00")), List.of());

        assertThrows(RuntimeException.class,
                () -> salesService.createReturn(cashReturn(returnItem(999L, "1", "100.00", "GOOD"))));
        verify(returnRepository, never()).save(any());
    }

    @Test
    void getOrderById_exposesNetRefundablePositionPerLine() {
        CustomerOrder order = completedOrder();
        order.setTotal(new BigDecimal("180.00"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderProductRepository.findByOrderId(1L)).thenReturn(List.of(soldLine(11L, 100L, "2", "100.00", "20.00")));
        SalesReturn prior = new SalesReturn();
        prior.setReturnId(50L);
        prior.setStatus("COMPLETED");
        when(returnRepository.findByOrderId(1L)).thenReturn(List.of(prior));
        when(returnItemRepository.findByReturnIdIn(List.of(50L))).thenReturn(List.of(priorReturnItem(11L, 100L, "1", "90.00")));

        var line = salesService.getOrderById(1L).getItems().get(0);

        assertThat(line.getNetLineTotal()).isEqualByComparingTo("180.00");
        assertThat(line.getNetUnitPrice()).isEqualByComparingTo("90.00");
        assertThat(line.getReturnedQuantity()).isEqualByComparingTo("1");
        assertThat(line.getReturnableQuantity()).isEqualByComparingTo("1");
        assertThat(line.getRefundableAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void createReturn_restocksPackUnitsInBaseUnits() {
        // 1 box of 12 sold; returning the box must put 12 base units back, not 1.
        CustomerOrder order = completedOrder();
        order.setTotal(new BigDecimal("1200.00"));
        OrderProduct box = soldLine(11L, 100L, "1", "1200.00", "0");
        box.setUnitId(4L);
        box.setBatchBarcode("BATCH-1");
        stubOrder(order, List.of(box), List.of());
        stubReturnSaves(99L);
        ItemUnit boxUnit = new ItemUnit();
        boxUnit.setUnitId(4L);
        boxUnit.setMultiplierToBase(new BigDecimal("12"));
        when(itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(100L, 4L)).thenReturn(Optional.of(boxUnit));
        com.pos.system.model.stock.Stock stock = new com.pos.system.model.stock.Stock();
        when(stockRepository.findByBranchIdAndItemIdAndVariantId(2L, 100L, null)).thenReturn(Optional.of(stock));
        com.pos.system.model.stock.StockBatch batch = new com.pos.system.model.stock.StockBatch();
        batch.setQtyRemaining(new BigDecimal("5"));
        batch.setAvailableQty(new BigDecimal("5"));
        when(stockBatchRepository.findByBranchIdAndInternalBatchBarcode(2L, "BATCH-1")).thenReturn(Optional.of(batch));

        salesService.createReturn(cashReturn(returnItem(100L, "1", "1200.00", "GOOD")));

        assertThat(batch.getQtyRemaining()).isEqualByComparingTo("17");
        assertThat(batch.getAvailableQty()).isEqualByComparingTo("17");
        ArgumentCaptor<com.pos.system.model.stock.StockMovement> movement =
                ArgumentCaptor.forClass(com.pos.system.model.stock.StockMovement.class);
        verify(stockMovementRepository).save(movement.capture());
        assertThat(movement.getValue().getQuantity()).isEqualByComparingTo("12");
        assertThat(movement.getValue().getUnitId()).isEqualTo(4L);
    }

    @Test
    void createReturn_onUnpaidOrder_acceptsGoodsAndReducesBalanceInsteadOfRefunding() {
        CustomerOrder order = completedOrder();
        order.setTotal(new BigDecimal("180.00"));
        order.setPaymentStatus("UNPAID");
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "100.00", "20.00")), List.of());
        when(paymentRepository.findByOrderId(1L)).thenReturn(List.of());
        stubReturnSaves(99L);

        SalesReturnResponse response = salesService.createReturn(cashReturn(returnItem(100L, "1", "100.00", "GOOD")));

        assertThat(response.getReturnValue()).isEqualByComparingTo("90.00");
        assertThat(response.getBalanceAdjustment()).isEqualByComparingTo("90.00");
        assertThat(response.getRefundAmount()).isEqualByComparingTo("0.00");
        verify(stockMovementRepository).save(any());          // goods still come back
        verifyNoInteractions(cashSessionTransactionRepository); // no money leaves the drawer
        assertThat(order.getPaymentStatus()).isEqualTo("UNPAID"); // 90 is still owed
    }

    @Test
    void createReturn_onPartlyPaidOrder_clearsBalanceFirstThenRefundsRemainder() {
        // 180 order, 100 paid, 80 owed. Returning one unit (90) clears the 80 and refunds 10.
        CustomerOrder order = completedOrder();
        order.setTotal(new BigDecimal("180.00"));
        order.setPaymentStatus("PARTIAL");
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "100.00", "20.00")), List.of());
        when(paymentRepository.findByOrderId(1L)).thenReturn(List.of(payment("100.00")));
        stubReturnSaves(99L);

        SalesReturnResponse response = salesService.createReturn(cashReturn(returnItem(100L, "1", "100.00", "GOOD")));

        assertThat(response.getBalanceAdjustment()).isEqualByComparingTo("80.00");
        assertThat(response.getRefundAmount()).isEqualByComparingTo("10.00");
        ArgumentCaptor<CashSessionTransaction> txn = ArgumentCaptor.forClass(CashSessionTransaction.class);
        verify(cashSessionTransactionRepository).save(txn.capture());
        assertThat(txn.getValue().getAmount()).isEqualByComparingTo("10.00");
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void processPayment_afterReturnOnUnpaidOrder_onlyChargesWhatIsStillOwed() {
        CustomerOrder order = completedOrder();
        order.setPaymentStatus("UNPAID");
        order.setTotal(new BigDecimal("180.00"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        SalesReturn prior = new SalesReturn();
        prior.setReturnId(50L);
        prior.setStatus("COMPLETED");
        prior.setBalanceAdjustment(new BigDecimal("90.00"));
        prior.setRefundAmount(BigDecimal.ZERO);
        when(returnRepository.findByOrderId(1L)).thenReturn(List.of(prior));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByOrderId(1L)).thenReturn(List.of());

        PaymentRequest.PaymentLineDto line = new PaymentRequest.PaymentLineDto();
        line.setPaymentMethod("CASH");
        line.setAmount(new BigDecimal("180.00"));
        line.setTenderedAmount(new BigDecimal("180.00"));
        PaymentRequest request = new PaymentRequest();
        request.setReceivedBy(7L);
        request.setPayments(List.of(line));

        salesService.processPayment(1L, request);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo("90.00");
        assertThat(paymentCaptor.getValue().getChangeAmount()).isEqualByComparingTo("90.00");
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void createReturn_onCreditSale_creditsCustomerAccountInsteadOfPayingCash() {
        CustomerOrder order = completedOrder();
        order.setCustomerId(5L);
        order.setTotal(new BigDecimal("180.00"));
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "100.00", "20.00")), List.of());
        Payment credit = payment("180.00");
        credit.setPaymentMethod("CREDIT");
        when(paymentRepository.findByOrderId(1L)).thenReturn(List.of(credit));
        stubReturnSaves(99L);

        SalesReturnResponse response = salesService.createReturn(cashReturn(returnItem(100L, "1", "100.00", "GOOD")));

        assertThat(response.getAccountCredit()).isEqualByComparingTo("90.00");
        assertThat(response.getRefundAmount()).isEqualByComparingTo("0.00");
        verify(customerService).recordReturnCredit(eq(5L), argThat(a -> a.compareTo(new BigDecimal("90.00")) == 0),
                eq(99L), eq("INV-0001"), eq(7L));
        verifyNoInteractions(cashSessionTransactionRepository);
    }

    @Test
    void createReturn_onMixedCreditAndCashSale_reversesCreditFirstThenPaysCash() {
        // 180 paid as 100 credit + 80 cash; a full return puts 100 back on account and pays 80 cash.
        CustomerOrder order = completedOrder();
        order.setCustomerId(5L);
        order.setTotal(new BigDecimal("180.00"));
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "100.00", "20.00")), List.of());
        Payment credit = payment("100.00");
        credit.setPaymentMethod("CREDIT");
        when(paymentRepository.findByOrderId(1L)).thenReturn(List.of(credit, payment("80.00")));
        stubReturnSaves(99L);

        SalesReturnResponse response = salesService.createReturn(cashReturn(returnItem(100L, "2", "100.00", "GOOD")));

        assertThat(response.getAccountCredit()).isEqualByComparingTo("100.00");
        assertThat(response.getRefundAmount()).isEqualByComparingTo("80.00");
        ArgumentCaptor<CashSessionTransaction> txn = ArgumentCaptor.forClass(CashSessionTransaction.class);
        verify(cashSessionTransactionRepository).save(txn.capture());
        assertThat(txn.getValue().getAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void createReturn_withBalanceAdjustmentMethod_givesStoreCredit() {
        CustomerOrder order = completedOrder();
        order.setCustomerId(5L);
        order.setTotal(new BigDecimal("180.00"));
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "100.00", "20.00")), List.of());
        stubReturnSaves(99L);
        SalesReturnRequest request = cashReturn(returnItem(100L, "1", "100.00", "GOOD"));
        request.setRefundMethod("BALANCE_ADJUSTMENT");

        SalesReturnResponse response = salesService.createReturn(request);

        assertThat(response.getAccountCredit()).isEqualByComparingTo("90.00");
        assertThat(response.getRefundAmount()).isEqualByComparingTo("0.00");
        verify(customerService).recordReturnCredit(eq(5L), any(), eq(99L), any(), any());
        verifyNoInteractions(cashSessionTransactionRepository);
    }

    @Test
    void createReturn_withBalanceAdjustmentMethod_requiresCustomer() {
        CustomerOrder order = completedOrder();
        order.setTotal(new BigDecimal("180.00"));
        stubOrder(order, List.of(soldLine(11L, 100L, "2", "100.00", "20.00")), List.of());
        SalesReturnRequest request = cashReturn(returnItem(100L, "1", "100.00", "GOOD"));
        request.setRefundMethod("BALANCE_ADJUSTMENT");

        assertThrows(RuntimeException.class, () -> salesService.createReturn(request));
        verify(returnRepository, never()).save(any());
        verifyNoInteractions(customerService);
    }
}
