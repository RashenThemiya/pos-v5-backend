package com.pos.system.service;

import com.pos.system.dto.sale.PaymentRequest;
import com.pos.system.dto.sale.SalesReturnRequest;
import com.pos.system.dto.sale.SalesReturnResponse;
import com.pos.system.model.sale.CustomerOrder;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
                customerService
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
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
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
    void createReturn_rejectsCancelledOrder() {
        CustomerOrder order = completedOrder();
        order.setStatus("CANCELLED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        SalesReturnRequest request = new SalesReturnRequest();
        request.setOrderId(1L);
        request.setItems(List.of(returnItem(100L, "1", "50.00", "GOOD")));

        assertThrows(RuntimeException.class, () -> salesService.createReturn(request));
        verifyNoInteractions(returnRepository);
    }
}
