package com.pos.system.service;

import com.pos.system.dto.customer.CustomerSearchRequest;
import com.pos.system.dto.customer.CustomerRequest;
import com.pos.system.model.auth.Authorization;
import com.pos.system.model.customer.Customer;
import com.pos.system.model.customer.CustomerBalanceTransaction;
import com.pos.system.model.customer.LoyaltyTransaction;
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
        customer.setCreditLimit(request.getCreditLimit());
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
        customer.setCreditLimit(request.getCreditLimit());
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
        Customer customer = getById(customerId);
        customer.setShopBalance(customer.getShopBalance().add(amount));
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);

        // Log balance transaction
        CustomerBalanceTransaction transaction = new CustomerBalanceTransaction();
        transaction.setCustomerId(customerId);
        transaction.setType("CREDIT");
        transaction.setAmount(amount);
        transaction.setNote(reason);
        transaction.setBranchId(customer.getBranchId());
        transaction.setRefTable("customer_balance");
        transaction.setRefId(customerId);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCreatedBy(getCurrentUserId());
        balanceTransactionRepository.save(transaction);
    }

    @Transactional
    public void deductBalance(Long customerId, BigDecimal amount, String reason) {
        Customer customer = getById(customerId);
        
        BigDecimal newBalance = customer.getShopBalance().subtract(amount);
        
        // Check if customer has enough credit to go negative
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            // Balance will be negative - check if customer has credit available
            BigDecimal debtAmount = newBalance.abs(); // e.g., 2000
            
            if (debtAmount.compareTo(customer.getCreditLimit()) > 0) {
                throw new RuntimeException("Insufficient balance and credit. Credit limit: " + 
                        customer.getCreditLimit() + ", Debt required: " + debtAmount);
            }
        }
        
        // Deduct balance (can now be negative) - creditLimit stays the same
        customer.setShopBalance(newBalance);
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);

        // Log balance transaction
        CustomerBalanceTransaction transaction = new CustomerBalanceTransaction();
        transaction.setCustomerId(customerId);
        transaction.setBranchId(customer.getBranchId());
        transaction.setType("DEBIT");
        transaction.setAmount(amount);
        transaction.setNote(reason);
        transaction.setRefTable("customer_balance");
        transaction.setRefId(customerId);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCreatedBy(getCurrentUserId());
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
                cb.like(cb.lower(root.get("phone")), like),
                cb.like(cb.lower(root.get("nic")), like),
                cb.like(cb.lower(root.get("loyaltyCardNo")), like)
        );
    }

    private String likePattern(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}