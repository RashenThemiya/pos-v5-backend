package com.pos.system.controller;

import com.pos.system.dto.common.ApiResponse;
import com.pos.system.dto.customer.*;
import com.pos.system.model.customer.Customer;
import com.pos.system.model.customer.CustomerBalanceTransaction;
import com.pos.system.model.customer.LoyaltyTransaction;
import com.pos.system.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@CrossOrigin
public class CustomerController {

    private final CustomerService customerService;

    // ==================== CUSTOMER CRUD ====================

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@RequestBody CustomerRequest request) {
        Customer customer = customerService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Customer created successfully", toResponse(customer)));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_VIEW')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getAll() {
        List<CustomerResponse> customers = customerService.getAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Customers fetched successfully", customers));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_VIEW')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Customer fetched successfully", toResponse(customerService.getById(id))));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_VIEW')")
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getByBranch(@PathVariable Long branchId) {
        List<CustomerResponse> customers = customerService.getByBranch(branchId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Customers fetched successfully", customers));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_VIEW')")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<CustomerResponse>> searchByPhone(@RequestParam String phone) {
        return ResponseEntity.ok(ApiResponse.success("Customer fetched successfully", toResponse(customerService.searchByPhone(phone))));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(@PathVariable Long id,
                                                                @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Customer updated successfully", toResponse(customerService.update(id, request))));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Customer deleted successfully", null));
    }

    // ==================== BALANCE TRANSACTION ENDPOINTS ====================

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_VIEW')")
    @GetMapping("/{customerId}/balance-transactions")
    public ResponseEntity<ApiResponse<List<CustomerBalanceTransactionResponse>>> getBalanceTransactions(
            @PathVariable Long customerId,
            @RequestParam(required = false) String type) {
        List<CustomerBalanceTransactionResponse> transactions;

        if (type != null && !type.isEmpty()) {
            transactions = customerService.getBalanceTransactionsByType(customerId, type)
                    .stream()
                    .map(this::toBalanceTransactionResponse)
                    .collect(Collectors.toList());
        } else {
            transactions = customerService.getBalanceTransactions(customerId)
                    .stream()
                    .map(this::toBalanceTransactionResponse)
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(ApiResponse.success("Balance transactions fetched successfully", transactions));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_UPDATE')")
    @PostMapping("/{customerId}/balance/add")
    public ResponseEntity<ApiResponse<CustomerResponse>> addBalance(
            @PathVariable Long customerId,
            @RequestBody AddBalanceRequest request) {
        customerService.addBalance(customerId, request.getAmount(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Balance added successfully", toResponse(customerService.getById(customerId))));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_UPDATE')")
    @PostMapping("/{customerId}/balance/deduct")
    public ResponseEntity<ApiResponse<CustomerResponse>> deductBalance(
            @PathVariable Long customerId,
            @RequestBody AddBalanceRequest request) {
        customerService.deductBalance(customerId, request.getAmount(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Balance deducted successfully", toResponse(customerService.getById(customerId))));
    }

    // ==================== LOYALTY TRANSACTION ENDPOINTS ====================

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_VIEW')")
    @GetMapping("/{customerId}/loyalty-transactions")
    public ResponseEntity<ApiResponse<List<LoyaltyTransactionResponse>>> getLoyaltyTransactions(@PathVariable Long customerId) {
        List<LoyaltyTransactionResponse> transactions = customerService.getLoyaltyTransactions(customerId)
                .stream()
                .map(this::toLoyaltyTransactionResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Loyalty transactions fetched successfully", transactions));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_VIEW')")
    @GetMapping("/{customerId}/loyalty-transactions/type/{type}")
    public ResponseEntity<ApiResponse<List<LoyaltyTransactionResponse>>> getLoyaltyTransactionsByType(
            @PathVariable Long customerId,
            @PathVariable String type) {
        List<LoyaltyTransactionResponse> transactions = customerService.getLoyaltyTransactionsByType(customerId, type)
                .stream()
                .map(this::toLoyaltyTransactionResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Loyalty transactions fetched successfully", transactions));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_UPDATE')")
    @PostMapping("/{customerId}/loyalty/earn")
    public ResponseEntity<ApiResponse<CustomerResponse>> earnPoints(
            @PathVariable Long customerId,
            @RequestBody EarnPointsRequest request) {
        customerService.earnPoints(customerId, request.getPoints(), request.getValueAmount(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Points earned successfully", toResponse(customerService.getById(customerId))));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_UPDATE')")
    @PostMapping("/{customerId}/loyalty/redeem")
    public ResponseEntity<ApiResponse<CustomerResponse>> redeemPoints(
            @PathVariable Long customerId,
            @RequestBody RedeemPointsRequest request) {
        customerService.redeemPoints(customerId, request.getPoints(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Points redeemed successfully", toResponse(customerService.getById(customerId))));
    }


    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_VIEW')")
    @GetMapping("/branch/page/{branchId}")
    public Page<Customer> getCustomersByBranch(
            @PathVariable Long branchId,
            Pageable pageable) {

        return customerService.getByBranch(branchId, pageable);
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('CUSTOMER_VIEW')")
    @GetMapping("/page")
    public Page<Customer> getAllCustomers(Pageable pageable) {
        return customerService.getAll(pageable);
    }

    // ==================== HELPER METHODS ====================

    private CustomerResponse toResponse(Customer customer) {
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

    private CustomerBalanceTransactionResponse toBalanceTransactionResponse(CustomerBalanceTransaction transaction) {
        CustomerBalanceTransactionResponse response = new CustomerBalanceTransactionResponse();
        response.setTxnId(transaction.getTxnId());
        response.setBranchId(transaction.getBranchId());
        response.setCustomerId(transaction.getCustomerId());
        response.setType(transaction.getType());
        response.setAmount(transaction.getAmount());
        response.setRefTable(transaction.getRefTable());
        response.setRefId(transaction.getRefId());
        response.setNote(transaction.getNote());
        response.setCreatedBy(transaction.getCreatedBy());
        response.setCreatedAt(transaction.getCreatedAt());
        return response;
    }

    private LoyaltyTransactionResponse toLoyaltyTransactionResponse(LoyaltyTransaction transaction) {
        LoyaltyTransactionResponse response = new LoyaltyTransactionResponse();
        response.setLoyaltyTxnId(transaction.getLoyaltyTxnId());
        response.setBranchId(transaction.getBranchId());
        response.setCustomerId(transaction.getCustomerId());
        response.setOrderId(transaction.getOrderId());
        response.setType(transaction.getType());
        response.setPoints(transaction.getPoints());
        response.setValueAmount(transaction.getValueAmount());
        response.setNote(transaction.getNote());
        response.setCreatedBy(transaction.getCreatedBy());
        response.setCreatedAt(transaction.getCreatedAt());
        return response;
    }
}