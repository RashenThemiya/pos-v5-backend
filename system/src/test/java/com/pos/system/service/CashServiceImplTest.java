package com.pos.system.service;

import com.pos.system.dto.cash.SessionSummaryResponse;
import com.pos.system.model.cash.CashSession;
import com.pos.system.model.cash.CashSessionTransaction;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.model.sale.Payment;
import com.pos.system.repository.CashSessionDenominationRepository;
import com.pos.system.repository.CashSessionRepository;
import com.pos.system.repository.CashSessionTransactionRepository;
import com.pos.system.repository.CounterRepository;
import com.pos.system.repository.CustomerOrderRepository;
import com.pos.system.repository.ExpenseRepository;
import com.pos.system.repository.PaymentRepository;
import com.pos.system.repository.WithdrawalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashServiceImplTest {

    @Mock private CounterRepository counterRepository;
    @Mock private CashSessionRepository cashSessionRepository;
    @Mock private CashSessionDenominationRepository denominationRepository;
    @Mock private CashSessionTransactionRepository transactionRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private WithdrawalRepository withdrawalRepository;
    @Mock private CustomerOrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;

    private CashServiceImpl cashService;

    @BeforeEach
    void setUp() {
        cashService = new CashServiceImpl(
                counterRepository,
                cashSessionRepository,
                denominationRepository,
                transactionRepository,
                expenseRepository,
                withdrawalRepository,
                orderRepository,
                paymentRepository
        );
    }

    @Test
    void getSessionSummary_usesInvoiceTotalForOverTenderedCashSale() {
        CashSession session = openSession("6200.00");
        CustomerOrder order = completedOrder("300.00");
        Payment overTenderedCashPayment = cashPayment("500.00", "500.00");
        CashSessionTransaction badSaleTxn = transaction("SALE", "500.00", "CASH");
        CashSessionTransaction supplierPayment = transaction("SUPPLIER_PAYMENT_OUT", "1000.00", "CASH");

        when(cashSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(transactionRepository.findBySessionIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(badSaleTxn, supplierPayment));
        when(orderRepository.findByCashSessionIdOrderByOrderDateDesc(10L)).thenReturn(List.of(order));
        when(paymentRepository.findByOrderId(77L)).thenReturn(List.of(overTenderedCashPayment));

        SessionSummaryResponse summary = cashService.getSessionSummary(10L);

        assertThat(summary.getTotalCashSales()).isEqualByComparingTo("300.00");
        assertThat(summary.getTotalSupplierPayments()).isEqualByComparingTo("1000.00");
        assertThat(summary.getExpectedCash()).isEqualByComparingTo("5500.00");
    }

    @Test
    void getSessionSummary_neverReturnsNegativeExpectedCash() {
        CashSession session = openSession("500.00");
        CashSessionTransaction supplierPayment = transaction("SUPPLIER_PAYMENT_OUT", "1000.00", "CASH");

        when(cashSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(transactionRepository.findBySessionIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(supplierPayment));
        when(orderRepository.findByCashSessionIdOrderByOrderDateDesc(10L)).thenReturn(List.of());

        SessionSummaryResponse summary = cashService.getSessionSummary(10L);

        assertThat(summary.getExpectedCash()).isEqualByComparingTo("0.00");
    }

    private CashSession openSession(String openingCash) {
        CashSession session = new CashSession();
        session.setSessionId(10L);
        session.setCounterId(1L);
        session.setOpenedBy(7L);
        session.setOpeningCash(new BigDecimal(openingCash));
        session.setStatus("OPEN");
        return session;
    }

    private CustomerOrder completedOrder(String total) {
        CustomerOrder order = new CustomerOrder();
        order.setOrderId(77L);
        order.setCashSessionId(10L);
        order.setInvoiceNo("INV-77");
        order.setStatus("COMPLETED");
        order.setTotal(new BigDecimal(total));
        return order;
    }

    private Payment cashPayment(String amount, String tenderedAmount) {
        Payment payment = new Payment();
        payment.setOrderId(77L);
        payment.setCashSessionId(10L);
        payment.setPaymentMethod("CASH");
        payment.setAmount(new BigDecimal(amount));
        payment.setTenderedAmount(new BigDecimal(tenderedAmount));
        return payment;
    }

    private CashSessionTransaction transaction(String type, String amount, String paymentMethod) {
        CashSessionTransaction txn = new CashSessionTransaction();
        txn.setSessionId(10L);
        txn.setType(type);
        txn.setAmount(new BigDecimal(amount));
        txn.setPaymentMethod(paymentMethod);
        txn.setOrderId("SALE".equals(type) ? 77L : null);
        txn.setInvoiceNo("SALE".equals(type) ? "INV-77" : null);
        return txn;
    }
}
