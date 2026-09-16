package com.pos.system.service;

import com.pos.system.dto.customer.CustomerSearchRequest;
import com.pos.system.dto.customer.CustomerRequest;
import com.pos.system.dto.cash.CashSessionTransactionResponse;
import com.pos.system.dto.customer.CustomerBalanceTransactionResponse;
import com.pos.system.dto.customer.CustomerPaymentRequest;
import com.pos.system.dto.customer.CustomerPaymentResponse;
import com.pos.system.model.auth.Authorization;
import com.pos.system.model.cash.CashSession;
import com.pos.system.model.cash.CashSessionTransaction;
import com.pos.system.model.cash.Counter;
import com.pos.system.model.customer.Customer;
import com.pos.system.model.customer.CustomerBalanceTransaction;
import com.pos.system.model.customer.LoyaltyTransaction;
import com.pos.system.repository.CashSessionRepository;
import com.pos.system.repository.CashSessionTransactionRepository;
import com.pos.system.repository.CounterRepository;
import com.pos.system.repository.CustomerRepository;
import com.pos.system.repository.AuthorizationRepository;
import com.pos.system.repository.CustomerBalanceTransactionRepository;
import com.pos.system.repository.LoyaltyTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AuthorizationRepository authorizationRepository; 
    private final CustomerBalanceTransactionRepository balanceTransactionRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final CashSessionRepository cashSessionRepository;
    private final CashSessionTransactionRepository cashSessionTransactionRepository;
    private final CounterRepository counterRepository;

    public Customer create(CustomerRequest request) {
        // Validate unique constraints
        if (request.getPhone() != null && customerRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("Phone number already exists");
        }

        if (request.getNic() != null && customerRepository.existsByNic(request.getNic())) {
            throw new RuntimeException("NIC already exists");
        }

        Customer customer = new Customer();
        customer.setBranchId(request.getBranchId());
        customer.setAuthId(request.getAuthId());
        customer.setName(request.getName());
        customer.setNic(request.getNic());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());
        customer.setCreditLimit(nonNegativeMoney(request.getCreditLimit()));
        customer.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());

        return customerRepository.save(customer);
    }

    public List<Customer> getAll() {
        return customerRepository.findAll();
    }

    public Customer getById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
    }

    public Customer update(Long id, CustomerRequest request) {
        Customer customer = getById(id);
        customer.setName(request.getName());
        customer.setNic(request.getNic());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());
        BigDecimal creditLimit = nonNegativeMoney(request.getCreditLimit());
        BigDecimal outstandingDebt = outstandingDebt(customer);
        if (outstandingDebt.compareTo(creditLimit) > 0) {
            throw new RuntimeException("Credit limit cannot be below outstanding balance. Outstanding balance: "
                    + outstandingDebt + ", requested credit limit: " + creditLimit);
        }
        customer.setCreditLimit(creditLimit);
        if (request.getIsActive() != null) {
            customer.setIsActive(request.getIsActive());
        }
        customer.setUpdatedAt(LocalDateTime.now());
        return customerRepository.save(customer);
    }

    public void delete(Long id) {
        Customer customer = getById(id);
        customerRepository.delete(customer);
    }

    public List<Customer> getByBranch(Long branchId) {
        return customerRepository.findByBranchId(branchId);
    }

    public Page<Customer> getByBranch(Long branchId, Pageable pageable) {
        return customerRepository.findByBranchId(branchId, pageable);
    }

    public Page<Customer> getAll(Pageable pageable) {
        return customerRepository.findAll(pageable);
    }

    public Page<Customer> search(CustomerSearchRequest request, Pageable pageable) {
        Specification<Customer> specification = (root, query, cb) -> cb.conjunction();

        if (request.getBranchId() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("branchId"), request.getBranchId()));
        }

        if (StringUtils.hasText(request.getQ())) {
            specification = specification.and(buildSearchSpecification(request.getQ()));
        }

        return customerRepository.findAll(specification, pageable);
    }

    public Customer searchByPhone(String phone) {
        return customerRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("Customer not found with phone: " + phone));
    }

    @Transactional
    public void addBalance(Long customerId, BigDecimal amount, String reason) {
        requireReason(reason);
        applyBalanceChange(
                customerId,
                amount,
                reason,
                "MANUAL_ADD_ADJUSTMENT",
                "customer_balance",
                customerId,
                getCurrentUserId(),
                true
        );
    }

    @Transactional
    public void deductBalance(Long customerId, BigDecimal amount, String reason) {
        requireReason(reason);
        applyBalanceChange(
                customerId,
                amount,
                reason,
                "MANUAL_DEDUCT_ADJUSTMENT",
                "customer_balance",
                customerId,
                getCurrentUserId(),
                false
        );
    }

    @Transactional
    public void recordCreditSale(Long customerId, BigDecimal amount, Long orderId, String invoiceNo, Long createdBy) {
        applyBalanceChange(
                customerId,
                amount,
                "Credit sale invoice: " + invoiceNo,
                "CREDIT_SALE",
                "customer_orders",
                orderId,
                createdBy,
                false
        );
    }

    @Transactional
    public CustomerPaymentResponse createCustomerPayment(Long customerId, CustomerPaymentRequest request) {
        if (request == null) {
            throw new RuntimeException("Payment request is required");
        }
        if (request.getBranchId() == null) {
            throw new RuntimeException("Branch is required");
        }

        Customer customer = getById(customerId);
        if (!request.getBranchId().equals(customer.getBranchId())) {
            throw new RuntimeException("Customer does not belong to this branch");
        }

        BigDecimal paymentAmount = positiveMoney(request.getAmount());
        BigDecimal previousShopBalance = moneyOrZero(customer.getShopBalance());
        BigDecimal previousOutstanding = outstandingDebt(previousShopBalance);
        if (previousOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Customer has no outstanding credit balance");
        }
        if (paymentAmount.compareTo(previousOutstanding) > 0) {
            throw new RuntimeException("Payment amount exceeds outstanding balance");
        }

        String paymentMethod = normalizePaymentMethod(request.getPaymentMethod());
        Long receivedBy = request.getReceivedBy() != null ? request.getReceivedBy() : getCurrentUserId();
        CashSession cashSession = null;
        Counter counter = null;

        if (isCashPayment(paymentMethod)) {
            if (request.getCashSessionId() == null) {
                throw new RuntimeException("Cash session is required for CASH payment");
            }
            if (request.getCounterId() == null) {
                throw new RuntimeException("Counter is required for CASH payment");
            }

            cashSession = cashSessionRepository.findById(request.getCashSessionId())
                    .orElseThrow(() -> new RuntimeException("Cash session not found"));

            if (!"OPEN".equalsIgnoreCase(cashSession.getStatus())) {
                throw new RuntimeException("Cash session is not OPEN");
            }
            if (!request.getCounterId().equals(cashSession.getCounterId())) {
                throw new RuntimeException("Cash session does not belong to this counter");
            }

            counter = counterRepository.findById(request.getCounterId())
                    .orElseThrow(() -> new RuntimeException("Counter not found"));
            if (!request.getBranchId().equals(counter.getBranchId())) {
                throw new RuntimeException("Counter does not belong to this branch");
            }
        }

        BigDecimal newShopBalance = previousShopBalance.add(paymentAmount);
        customer.setShopBalance(newShopBalance);
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);

        CustomerBalanceTransaction paymentTransaction = new CustomerBalanceTransaction();
        paymentTransaction.setBranchId(customer.getBranchId());
        paymentTransaction.setCustomerId(customerId);
        paymentTransaction.setType("CUSTOMER_PAYMENT");
        paymentTransaction.setAmount(paymentAmount);
        paymentTransaction.setPreviousBalance(previousOutstanding);
        paymentTransaction.setNewBalance(outstandingDebt(newShopBalance));
        paymentTransaction.setRefTable("customer_payments");
        paymentTransaction.setNote(resolvePaymentNote(request.getNote(), paymentMethod));
        paymentTransaction.setCreatedBy(receivedBy);
        paymentTransaction.setCreatedAt(LocalDateTime.now());
        CustomerBalanceTransaction savedPaymentTransaction = balanceTransactionRepository.save(paymentTransaction);

        savedPaymentTransaction.setRefId(savedPaymentTransaction.getTxnId());
        savedPaymentTransaction = balanceTransactionRepository.save(savedPaymentTransaction);

        CashSessionTransaction savedCashTransaction = null;
        if (isCashPayment(paymentMethod)) {
            CashSessionTransaction cashTransaction = new CashSessionTransaction();
            cashTransaction.setSessionId(cashSession.getSessionId());
            cashTransaction.setType("CUSTOMER_PAYMENT_IN");
            cashTransaction.setAmount(paymentAmount);
            cashTransaction.setPaymentMethod(paymentMethod);
            cashTransaction.setCounterId(counter.getCounterId());
            cashTransaction.setReferenceNo(StringUtils.hasText(request.getReferenceNo())
                    ? request.getReferenceNo().trim()
                    : "CUSTOMER_PAYMENT-" + savedPaymentTransaction.getTxnId());
            cashTransaction.setNote(resolvePaymentNote(request.getNote(), paymentMethod));
            cashTransaction.setCreatedBy(receivedBy);
            cashTransaction.setCreatedAt(LocalDateTime.now());
            savedCashTransaction = cashSessionTransactionRepository.save(cashTransaction);
        }

        return CustomerPaymentResponse.builder()
                .paymentTransaction(toBalanceTransactionResponse(savedPaymentTransaction))
                .updatedOutstandingBalance(outstandingDebt(newShopBalance))
                .cashSessionTransaction(savedCashTransaction != null ? toCashSessionTransactionResponse(savedCashTransaction) : null)
                .build();
    }

    private void applyBalanceChange(
            Long customerId,
            BigDecimal amount,
            String reason,
            String transactionType,
            String refTable,
            Long refId,
            Long createdBy,
            boolean addToShopBalance
    ) {
        Customer customer = getById(customerId);
        BigDecimal balanceAmount = positiveMoney(amount);
        BigDecimal previousShopBalance = moneyOrZero(customer.getShopBalance());
        BigDecimal newShopBalance = addToShopBalance
                ? previousShopBalance.add(balanceAmount)
                : previousShopBalance.subtract(balanceAmount);
        
        // Check if customer has enough credit to go negative
        if (newShopBalance.compareTo(BigDecimal.ZERO) < 0) {
            // Balance will be negative - check if customer has credit available
            BigDecimal debtAmount = newShopBalance.abs(); // e.g., 2000
            
            BigDecimal creditLimit = moneyOrZero(customer.getCreditLimit());
            if (debtAmount.compareTo(creditLimit) > 0) {
                throw new RuntimeException("Insufficient balance and credit. Credit limit: " + 
                        creditLimit + ", Debt required: " + debtAmount);
            }
        }
        
        // Deduct balance (can now be negative) - creditLimit stays the same
        customer.setShopBalance(newShopBalance);
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);

        // Log balance transaction
        CustomerBalanceTransaction transaction = new CustomerBalanceTransaction();
        transaction.setCustomerId(customerId);
        transaction.setBranchId(customer.getBranchId());
        transaction.setType(transactionType);
        transaction.setAmount(balanceAmount);
        transaction.setPreviousBalance(outstandingDebt(previousShopBalance));
        transaction.setNewBalance(outstandingDebt(newShopBalance));
        transaction.setNote(reason);
        transaction.setRefTable(refTable);
        transaction.setRefId(refId);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCreatedBy(createdBy != null ? createdBy : getCurrentUserId());
        balanceTransactionRepository.save(transaction);
    }

    @Transactional
    public void earnPoints(Long customerId, BigDecimal points, BigDecimal valueAmount, String reason) {
        Customer customer = getById(customerId);
        customer.setLoyaltyPoints(customer.getLoyaltyPoints().add(points));
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);

        // Log loyalty transaction
        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setCustomerId(customerId);
        transaction.setType("EARN");
        transaction.setBranchId(customer.getBranchId());
        transaction.setPoints(points);
        transaction.setValueAmount(valueAmount);
        transaction.setNote(reason);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCreatedBy(getCurrentUserId()); 
        loyaltyTransactionRepository.save(transaction);
    }

    @Transactional
    public void redeemPoints(Long customerId, BigDecimal points, String reason) {
        Customer customer = getById(customerId);
        if (customer.getLoyaltyPoints().compareTo(points) < 0) {
            throw new RuntimeException("Insufficient loyalty points");
        }
        customer.setLoyaltyPoints(customer.getLoyaltyPoints().subtract(points));
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);

        // Calculate value amount (assuming 1:1 for now; integrate LoyaltySetting later)
        BigDecimal valueAmount = points;

        // Log loyalty transaction
        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setCustomerId(customerId);
        transaction.setType("REDEEM");
        transaction.setPoints(points);
        transaction.setBranchId(customer.getBranchId());
        transaction.setValueAmount(valueAmount);
        transaction.setNote(reason);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCreatedBy(getCurrentUserId());
        loyaltyTransactionRepository.save(transaction);
    }

    public List<CustomerBalanceTransaction> getBalanceTransactions(Long customerId) {
        return balanceTransactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    public List<CustomerBalanceTransaction> getBalanceTransactionsByType(Long customerId, String type) {
        return balanceTransactionRepository.findByCustomerIdAndType(customerId, type);
    }

    public List<LoyaltyTransaction> getLoyaltyTransactions(Long customerId) {
        return loyaltyTransactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    public List<LoyaltyTransaction> getLoyaltyTransactionsByType(Long customerId, String type) {
        return loyaltyTransactionRepository.findByCustomerIdAndType(customerId, type);
    }

    public String findCreatedByName(Long authId) {
        return authorizationRepository.findById(authId)
                .map(Authorization::getUsername)
                .orElse(null);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated()) {
            // Get username from the authentication principal
            String username = authentication.getName();
            
            // Look up the Authorization entity to get the authId
            Authorization auth = authorizationRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            return auth.getAuthId();
        }
        
        return 1L; // Fallback for testing (remove in production)
    }

    private Specification<Customer> buildSearchSpecification(String queryText) {
        String like = likePattern(queryText);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("phone")), like),
                cb.like(cb.lower(root.get("nic")), like),
                cb.like(cb.lower(root.get("loyaltyCardNo")), like)
        );
    }

    private String likePattern(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }

    private BigDecimal moneyOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal nonNegativeMoney(BigDecimal value) {
        return moneyOrZero(value).max(BigDecimal.ZERO);
    }

    private BigDecimal positiveMoney(BigDecimal value) {
        BigDecimal amount = moneyOrZero(value);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }
        return amount;
    }

    private void requireReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new RuntimeException("Reason is required for manual balance adjustments");
        }
    }

    private BigDecimal outstandingDebt(Customer customer) {
        return outstandingDebt(customer.getShopBalance());
    }

    private BigDecimal outstandingDebt(BigDecimal shopBalanceValue) {
        BigDecimal shopBalance = moneyOrZero(shopBalanceValue);
        return shopBalance.compareTo(BigDecimal.ZERO) < 0 ? shopBalance.abs() : BigDecimal.ZERO;
    }

    private String normalizePaymentMethod(String paymentMethod) {
        if (!StringUtils.hasText(paymentMethod)) {
            throw new RuntimeException("Payment method is required");
        }
        return paymentMethod.trim().toUpperCase();
    }

    private boolean isCashPayment(String paymentMethod) {
        return "CASH".equalsIgnoreCase(paymentMethod);
    }

    private String resolvePaymentNote(String note, String paymentMethod) {
        return StringUtils.hasText(note) ? note.trim() : "Customer credit repayment by " + paymentMethod;
    }

    private CustomerBalanceTransactionResponse toBalanceTransactionResponse(CustomerBalanceTransaction transaction) {
        CustomerBalanceTransactionResponse response = new CustomerBalanceTransactionResponse();
        response.setTxnId(transaction.getTxnId());
        response.setBranchId(transaction.getBranchId());
        response.setCustomerId(transaction.getCustomerId());
        response.setType(transaction.getType());
        response.setAmount(transaction.getAmount());
        response.setPreviousBalance(transaction.getPreviousBalance());
        response.setNewBalance(transaction.getNewBalance());
        response.setRefTable(transaction.getRefTable());
        response.setRefId(transaction.getRefId());
        response.setNote(transaction.getNote());
        response.setCreatedBy(transaction.getCreatedBy());
        response.setCreatedByName(findCreatedByName(transaction.getCreatedBy()));
        response.setCreatedAt(transaction.getCreatedAt());
        return response;
    }

    private CashSessionTransactionResponse toCashSessionTransactionResponse(CashSessionTransaction transaction) {
        return CashSessionTransactionResponse.builder()
                .id(transaction.getId())
                .sessionId(transaction.getSessionId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .paymentMethod(transaction.getPaymentMethod())
                .paymentId(transaction.getPaymentId())
                .supplierPaymentId(transaction.getSupplierPaymentId())
                .purchaseReturnId(transaction.getPurchaseReturnId())
                .supplierId(transaction.getSupplierId())
                .supplyId(transaction.getSupplyId())
                .counterId(transaction.getCounterId())
                .referenceNo(transaction.getReferenceNo())
                .expenseId(transaction.getExpenseId())
                .withdrawalId(transaction.getWithdrawalId())
                .orderId(transaction.getOrderId())
                .invoiceNo(transaction.getInvoiceNo())
                .salesReturnId(transaction.getSalesReturnId())
                .note(transaction.getNote())
                .createdBy(transaction.getCreatedBy())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

}
