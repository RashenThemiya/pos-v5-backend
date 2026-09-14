package com.pos.system.service;

import com.pos.system.dto.customer.CustomerPaymentRequest;
import com.pos.system.dto.customer.CustomerPaymentResponse;
import com.pos.system.model.cash.CashSession;
import com.pos.system.model.cash.CashSessionTransaction;
import com.pos.system.model.cash.Counter;
import com.pos.system.model.customer.Customer;
import com.pos.system.model.customer.CustomerBalanceTransaction;
import com.pos.system.repository.AuthorizationRepository;
import com.pos.system.repository.CashSessionRepository;
import com.pos.system.repository.CashSessionTransactionRepository;
import com.pos.system.repository.CounterRepository;
import com.pos.system.repository.CustomerBalanceTransactionRepository;
import com.pos.system.repository.CustomerRepository;
import com.pos.system.repository.LoyaltyTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServicePaymentTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private AuthorizationRepository authorizationRepository;
    @Mock private CustomerBalanceTransactionRepository balanceTransactionRepository;
    @Mock private LoyaltyTransactionRepository loyaltyTransactionRepository;
    @Mock private CashSessionRepository cashSessionRepository;
    @Mock private CashSessionTransactionRepository cashSessionTransactionRepository;
    @Mock private CounterRepository counterRepository;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(
                customerRepository,
                authorizationRepository,
                balanceTransactionRepository,
                loyaltyTransactionRepository,
                cashSessionRepository,
                cashSessionTransactionRepository,
                counterRepository
        );
    }

    @Test
    void createCustomerPayment_recordsCashRepaymentAndDrawerTransaction() {
        Customer customer = customerWithBalance("-2000.00");
        CashSession session = openSession();
        Counter counter = branchCounter();

        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(cashSessionRepository.findById(21L)).thenReturn(Optional.of(session));
        when(counterRepository.findById(2L)).thenReturn(Optional.of(counter));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceTransactionRepository.save(any(CustomerBalanceTransaction.class))).thenAnswer(invocation -> {
            CustomerBalanceTransaction txn = invocation.getArgument(0);
            if (txn.getTxnId() == null) {
                txn.setTxnId(99L);
            }
            return txn;
        });
        when(cashSessionTransactionRepository.save(any(CashSessionTransaction.class))).thenAnswer(invocation -> {
            CashSessionTransaction txn = invocation.getArgument(0);
            txn.setId(88L);
            return txn;
        });
        when(authorizationRepository.findById(1L)).thenReturn(Optional.empty());

        CustomerPaymentResponse response = customerService.createCustomerPayment(10L, cashPaymentRequest("1500.00"));

        assertThat(customer.getShopBalance()).isEqualByComparingTo("-500.00");
        assertThat(response.getPaymentTransaction().getType()).isEqualTo("CUSTOMER_PAYMENT");
        assertThat(response.getPaymentTransaction().getPreviousBalance()).isEqualByComparingTo("2000.00");
        assertThat(response.getPaymentTransaction().getNewBalance()).isEqualByComparingTo("500.00");
        assertThat(response.getUpdatedOutstandingBalance()).isEqualByComparingTo("500.00");
        assertThat(response.getCashSessionTransaction().getId()).isEqualTo(88L);
        assertThat(response.getCashSessionTransaction().getType()).isEqualTo("CUSTOMER_PAYMENT_IN");
        assertThat(response.getCashSessionTransaction().getAmount()).isEqualByComparingTo("1500.00");
        assertThat(response.getCashSessionTransaction().getCounterId()).isEqualTo(2L);
    }

    @Test
    void createCustomerPayment_rejectsAmountGreaterThanOutstandingBalance() {
        Customer customer = customerWithBalance("-1000.00");
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.createCustomerPayment(10L, cashPaymentRequest("1500.00")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Payment amount exceeds outstanding balance");

        verify(customerRepository, never()).save(any(Customer.class));
        verify(balanceTransactionRepository, never()).save(any(CustomerBalanceTransaction.class));
        verify(cashSessionTransactionRepository, never()).save(any(CashSessionTransaction.class));
    }

    @Test
    void createCustomerPayment_doesNotRequireSessionForNonCashPayment() {
        Customer customer = customerWithBalance("-2000.00");

        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceTransactionRepository.save(any(CustomerBalanceTransaction.class))).thenAnswer(invocation -> {
            CustomerBalanceTransaction txn = invocation.getArgument(0);
            if (txn.getTxnId() == null) {
                txn.setTxnId(100L);
            }
            return txn;
        });
        when(authorizationRepository.findById(1L)).thenReturn(Optional.empty());

        CustomerPaymentRequest request = cashPaymentRequest("500.00");
        request.setPaymentMethod("CARD");
        request.setCashSessionId(null);
        request.setCounterId(null);

        CustomerPaymentResponse response = customerService.createCustomerPayment(10L, request);

        assertThat(customer.getShopBalance()).isEqualByComparingTo("-1500.00");
        assertThat(response.getPaymentTransaction().getType()).isEqualTo("CUSTOMER_PAYMENT");
        assertThat(response.getCashSessionTransaction()).isNull();
        verify(cashSessionRepository, never()).findById(any());
        verify(counterRepository, never()).findById(any());
    }

    private Customer customerWithBalance(String shopBalance) {
        Customer customer = new Customer();
        customer.setCustomerId(10L);
        customer.setBranchId(1L);
        customer.setName("Test Customer");
        customer.setShopBalance(new BigDecimal(shopBalance));
        customer.setCreditLimit(new BigDecimal("5000.00"));
        customer.setLoyaltyPoints(BigDecimal.ZERO);
        return customer;
    }

    private CashSession openSession() {
        CashSession session = new CashSession();
        session.setSessionId(21L);
        session.setCounterId(2L);
        session.setStatus("OPEN");
        return session;
    }

    private Counter branchCounter() {
        Counter counter = new Counter();
        counter.setCounterId(2L);
        counter.setBranchId(1L);
        return counter;
    }

    private CustomerPaymentRequest cashPaymentRequest(String amount) {
        CustomerPaymentRequest request = new CustomerPaymentRequest();
        request.setBranchId(1L);
        request.setAmount(new BigDecimal(amount));
        request.setPaymentMethod("CASH");
        request.setCashSessionId(21L);
        request.setCounterId(2L);
        request.setReceivedBy(1L);
        request.setReferenceNo("REF-1");
        request.setNote("Customer credit repayment");
        return request;
    }
}
