package com.pos.system.service;

import com.pos.system.dto.cash.*;
import com.pos.system.model.cash.*;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.model.sale.Payment;
import com.pos.system.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class CashServiceImpl implements CashService {

    private final CounterRepository counterRepository;
    private final CashSessionRepository cashSessionRepository;
    private final CashSessionDenominationRepository denominationRepository;
    private final CashSessionTransactionRepository transactionRepository;
    private final ExpenseRepository expenseRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final CustomerOrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    // ─── Counter ────────────────────────────────────────────────────────────────

    @Override
    public CounterResponse createCounter(CounterRequest request) {
        counterRepository.findByBranchIdAndName(request.getBranchId(), request.getName())
                .ifPresent(x -> {
                    throw new RuntimeException("Counter name already exists in this branch");
                });

        Counter counter = new Counter();
        counter.setBranchId(request.getBranchId());
        counter.setName(request.getName());
        counter.setLocation(request.getLocation());
        counter.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        counter.setCreatedAt(LocalDateTime.now());
        counter.setUpdatedAt(LocalDateTime.now());

        return mapCounter(counterRepository.save(counter));
    }

    @Override
    public CounterResponse updateCounter(Long counterId, CounterRequest request) {
        Counter counter = findCounterById(counterId);
        counter.setName(request.getName());
        counter.setLocation(request.getLocation());
        if (request.getIsActive() != null) {
            counter.setIsActive(request.getIsActive());
        }
        counter.setUpdatedAt(LocalDateTime.now());
        return mapCounter(counterRepository.save(counter));
    }

    @Override
    public CounterResponse getCounterById(Long counterId) {
        return mapCounter(findCounterById(counterId));
    }

    @Override
    public List<CounterResponse> getCountersByBranch(Long branchId) {
        return counterRepository.findByBranchId(branchId)
                .stream()
                .map(this::mapCounter)
                .toList();
    }

    // ─── Session ─────────────────────────────────────────────────────────────────

    @Override
    public SessionResponse openSession(OpenSessionRequest request) {
        findCounterById(request.getCounterId());

        cashSessionRepository.findByCounterIdAndStatus(request.getCounterId(), "OPEN")
                .ifPresent(x -> {
                    throw new RuntimeException("Counter already has an open session (sessionId=" + x.getSessionId() + ")");
                });

        CashSession session = new CashSession();
        session.setCounterId(request.getCounterId());
        session.setOpenedBy(request.getOpenedBy());
        session.setOpenedAt(LocalDateTime.now());
        session.setOpeningCash(nvl(request.getOpeningCash()));
        session.setSessionType(clean(request.getSessionType()));
        session.setPurpose(clean(request.getPurpose()));
        session.setStatus("OPEN");

        CashSession saved = cashSessionRepository.save(session);

        saveDenominations(saved.getSessionId(), "OPENING", request.getDenominations());
        recordTransaction(
                saved.getSessionId(),
                "SESSION_OPEN",
                saved.getOpeningCash(),
                "CASH",
                null, null,
                null, null,
                saved.getPurpose() != null ? saved.getPurpose() : "Cash session opened",
                saved.getOpenedBy()
        );

        return buildSessionResponse(saved);
    }

    @Override
    public SessionResponse closeSession(Long sessionId, CloseSessionRequest request) {
        CashSession session = findSessionById(sessionId);

        if (!"OPEN".equals(session.getStatus())) {
            throw new RuntimeException("Session is not open");
        }

        BigDecimal expectedCash = calculateExpectedCash(sessionId, session.getOpeningCash());
        BigDecimal closingCash = nvl(request.getClosingCash());
        BigDecimal difference = closingCash.subtract(expectedCash);

        session.setClosedBy(request.getClosedBy());
        session.setClosedAt(LocalDateTime.now());
        session.setClosingCash(closingCash);
        session.setExpectedCash(expectedCash);
        session.setCashDifference(difference);
        session.setStatus("CLOSED");

        CashSession saved = cashSessionRepository.save(session);

        saveDenominations(saved.getSessionId(), "CLOSING", request.getDenominations());
        String closeNote = clean(request.getNote());
        recordTransaction(
                saved.getSessionId(),
                "SESSION_CLOSE",
                closingCash,
                "CASH",
                null, null,
                null, null,
                closeNote != null ? closeNote : "Cash session closed",
                request.getClosedBy()
        );

        return buildSessionResponse(saved);
    }

    @Override
    public SessionResponse getSessionById(Long sessionId) {
        return buildSessionResponse(findSessionById(sessionId));
    }

    @Override
    public SessionResponse getActiveSessionByCounter(Long counterId) {
        CashSession session = cashSessionRepository.findByCounterIdAndStatus(counterId, "OPEN")
                .orElseThrow(() -> new RuntimeException("No open session for counter: " + counterId));
        return buildSessionResponse(session);
    }

    @Override
    public List<SessionResponse> getSessionsByCounter(Long counterId) {
        return cashSessionRepository.findByCounterIdOrderByOpenedAtDesc(counterId)
                .stream()
                .map(this::buildSessionResponse)
                .toList();
    }

    // ─── Summary ─────────────────────────────────────────────────────────────────

    @Override
    public SessionSummaryResponse getSessionSummary(Long sessionId) {
        CashSession session = findSessionById(sessionId);
        List<CashSessionTransaction> txns = transactionRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);

        BigDecimal cashSales     = calculateCashSales(sessionId, txns);
        BigDecimal cardSales     = sumByTypeAndMethod(txns, "SALE", "CARD");
        BigDecimal otherSales    = sumByTypeNotMethod(txns, "SALE", "CASH", "CARD");
        BigDecimal expenses      = sumByType(txns, "EXPENSE");
        BigDecimal withdrawals   = sumByType(txns, "WITHDRAWAL");
        BigDecimal cashRefunds   = sumByTypeAndCashMethod(txns, "REFUND");
        BigDecimal supplierPays  = sumByTypes(txns, "SUPPLIER_PAYMENT", "SUPPLIER_PAYMENT_OUT");
        BigDecimal supplierRefunds = sumByTypes(txns, "SUPPLIER_REFUND_IN", "PURCHASE_RETURN_CASH_REFUND");

        BigDecimal expected = nvl(session.getOpeningCash())
                .add(cashSales)
                .add(supplierRefunds)
                .subtract(expenses)
                .subtract(withdrawals)
                .subtract(cashRefunds)
                .subtract(supplierPays)
                .max(BigDecimal.ZERO);

        return SessionSummaryResponse.builder()
                .sessionId(sessionId)
                .openingCash(session.getOpeningCash())
                .totalCashSales(cashSales)
                .totalCardSales(cardSales)
                .totalOtherSales(otherSales)
                .totalExpenses(expenses)
                .totalWithdrawals(withdrawals)
                .totalCashRefunds(cashRefunds)
                .totalSupplierPayments(supplierPays)
                .totalSupplierRefunds(supplierRefunds)
                .expectedCash(expected)
                .closingCash(session.getClosingCash())
                .cashDifference(session.getCashDifference())
                .status(session.getStatus())
                .build();
    }

    // ─── Transactions ─────────────────────────────────────────────────────────────

    @Override
    public List<CashSessionTransactionResponse> getTransactionsBySession(Long sessionId) {
        findSessionById(sessionId);
        return transactionRepository.findBySessionIdOrderByCreatedAtDesc(sessionId)
                .stream()
                .map(this::mapTransaction)
                .toList();
    }

    // ─── Expenses ────────────────────────────────────────────────────────────────

    @Override
    public ExpenseResponse addExpense(ExpenseRequest request) {
        if (request.getCashSessionId() != null) {
            CashSession session = findSessionById(request.getCashSessionId());
            if (!"OPEN".equals(session.getStatus())) {
                throw new RuntimeException("Cannot add expense to a closed session");
            }
        }

        Expense expense = new Expense();
        expense.setBranchId(request.getBranchId());
        expense.setCashSessionId(request.getCashSessionId());
        expense.setCategory(request.getCategory());
        expense.setAmount(nvl(request.getAmount()));
        expense.setPaymentMethod(request.getPaymentMethod());
        expense.setExpenseDate(request.getExpenseDate() != null ? request.getExpenseDate() : LocalDateTime.now());
        expense.setCreatedBy(request.getCreatedBy());
        expense.setNote(request.getNote());

        Expense saved = expenseRepository.save(expense);

        if (request.getCashSessionId() != null) {
            recordTransaction(
                    request.getCashSessionId(),
                    "EXPENSE",
                    saved.getAmount(),
                    request.getPaymentMethod(),
                    null, null,
                    saved.getExpenseId(),
                    null,
                    request.getNote(),
                    request.getCreatedBy()
            );
        }

        return mapExpense(saved);
    }

    @Override
    public List<ExpenseResponse> getExpensesBySession(Long sessionId) {
        findSessionById(sessionId);
        return expenseRepository.findByCashSessionIdOrderByExpenseDateDesc(sessionId)
                .stream()
                .map(this::mapExpense)
                .toList();
    }

    @Override
    public List<ExpenseResponse> getExpensesByBranch(Long branchId) {
        return expenseRepository.findByBranchIdOrderByExpenseDateDesc(branchId)
                .stream()
                .map(this::mapExpense)
                .toList();
    }

    // ─── Withdrawals ─────────────────────────────────────────────────────────────

    @Override
    public WithdrawalResponse addWithdrawal(WithdrawalRequest request) {
        CashSession session = findSessionById(request.getCashSessionId());
        if (!"OPEN".equals(session.getStatus())) {
            throw new RuntimeException("Cannot add withdrawal to a closed session");
        }

        BigDecimal amount = nvl(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Withdrawal amount must be greater than 0");
        }

        BigDecimal availableCash = calculateExpectedCash(request.getCashSessionId(), session.getOpeningCash());
        if (amount.compareTo(availableCash) > 0) {
            throw new RuntimeException("Withdrawal amount cannot exceed available cash");
        }

        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setCashSessionId(request.getCashSessionId());
        withdrawal.setAmount(amount);
        withdrawal.setReason(request.getReason());
        withdrawal.setNotes(request.getNotes());
        withdrawal.setWithdrawalDate(request.getWithdrawalDate() != null ? request.getWithdrawalDate() : LocalDateTime.now());
        withdrawal.setUserId(request.getUserId());

        Withdrawal saved = withdrawalRepository.save(withdrawal);

        recordTransaction(
                request.getCashSessionId(),
                "WITHDRAWAL",
                saved.getAmount(),
                "CASH",
                null, null, null,
                saved.getWithdrawalId(),
                saved.getReason(),
                request.getUserId()
        );

        return mapWithdrawal(saved);
    }

    @Override
    public List<WithdrawalResponse> getWithdrawalsBySession(Long sessionId) {
        findSessionById(sessionId);
        return withdrawalRepository.findByCashSessionIdOrderByWithdrawalDateDesc(sessionId)
                .stream()
                .map(this::mapWithdrawal)
                .toList();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────

    private void recordTransaction(Long sessionId, String type, BigDecimal amount,
                                   String paymentMethod, Long paymentId, Long supplierPaymentId,
                                   Long expenseId, Long withdrawalId, String note, Long createdBy) {
        CashSessionTransaction txn = new CashSessionTransaction();
        txn.setSessionId(sessionId);
        txn.setType(type);
        txn.setAmount(amount);
        txn.setPaymentMethod(paymentMethod);
        txn.setPaymentId(paymentId);
        txn.setSupplierPaymentId(supplierPaymentId);
        txn.setExpenseId(expenseId);
        txn.setWithdrawalId(withdrawalId);
        txn.setNote(note);
        txn.setCreatedBy(createdBy != null ? createdBy : 1L);
        txn.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(txn);
    }

    private void saveDenominations(Long sessionId, String type, List<DenominationDto> denominations) {
        if (denominations == null || denominations.isEmpty()) return;
        for (DenominationDto d : denominations) {
            CashSessionDenomination entity = new CashSessionDenomination();
            entity.setSessionId(sessionId);
            entity.setType(type);
            entity.setDenomination(d.getDenomination());
            entity.setQty(d.getQty());
            entity.setTotal(d.getDenomination().multiply(BigDecimal.valueOf(d.getQty())));
            denominationRepository.save(entity);
        }
    }

    private BigDecimal calculateExpectedCash(Long sessionId, BigDecimal openingCash) {
        List<CashSessionTransaction> txns = transactionRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
        BigDecimal cashIn  = calculateCashSales(sessionId, txns);
        BigDecimal expenses = sumByType(txns, "EXPENSE");
        BigDecimal withdrawals = sumByType(txns, "WITHDRAWAL");
        BigDecimal cashRefunds = sumByTypeAndCashMethod(txns, "REFUND");
        BigDecimal supplierPayments = sumByTypes(txns, "SUPPLIER_PAYMENT", "SUPPLIER_PAYMENT_OUT");
        BigDecimal supplierRefunds = sumByTypes(txns, "SUPPLIER_REFUND_IN", "PURCHASE_RETURN_CASH_REFUND");
        return nvl(openingCash)
                .add(cashIn)
                .add(supplierRefunds)
                .subtract(expenses)
                .subtract(withdrawals)
                .subtract(cashRefunds)
                .subtract(supplierPayments)
                .max(BigDecimal.ZERO);
    }

    private BigDecimal calculateCashSales(Long sessionId, List<CashSessionTransaction> txns) {
        List<CustomerOrder> orders = orderRepository.findByCashSessionIdOrderByOrderDateDesc(sessionId)
                .stream()
                .filter(this::isCompletedOrder)
                .toList();

        Set<Long> orderIds = new HashSet<>();
        Set<String> invoiceNos = new HashSet<>();

        BigDecimal reconciledCashSales = orders.stream()
                .peek(order -> {
                    if (order.getOrderId() != null) orderIds.add(order.getOrderId());
                    if (order.getInvoiceNo() != null) invoiceNos.add(order.getInvoiceNo());
                })
                .map(order -> calculateOrderCashSale(sessionId, order))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal legacyCashSales = txns.stream()
                .filter(t -> "SALE".equals(t.getType()) && isCashPayment(t.getPaymentMethod()))
                .filter(t -> t.getOrderId() == null || !orderIds.contains(t.getOrderId()))
                .filter(t -> t.getInvoiceNo() == null || !invoiceNos.contains(t.getInvoiceNo()))
                .map(t -> nvl(t.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return reconciledCashSales.add(legacyCashSales);
    }

    private BigDecimal calculateOrderCashSale(Long sessionId, CustomerOrder order) {
        List<Payment> payments = paymentRepository.findByOrderId(order.getOrderId());
        BigDecimal cashPaid = payments.stream()
                .filter(payment -> isPaymentInSession(payment, sessionId))
                .filter(payment -> isCashPayment(payment.getPaymentMethod()))
                .map(payment -> nvl(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal nonCashPaid = payments.stream()
                .filter(payment -> !isCashPayment(payment.getPaymentMethod()))
                .map(payment -> nvl(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashShareOfInvoice = nvl(order.getTotal()).subtract(nonCashPaid).max(BigDecimal.ZERO);
        return cashPaid.min(cashShareOfInvoice);
    }

    private boolean isPaymentInSession(Payment payment, Long sessionId) {
        return payment.getCashSessionId() == null || payment.getCashSessionId().equals(sessionId);
    }

    private boolean isCompletedOrder(CustomerOrder order) {
        return "COMPLETED".equalsIgnoreCase(order.getStatus());
    }

    private boolean isCashPayment(String paymentMethod) {
        return "CASH".equalsIgnoreCase(paymentMethod)
                || "COUNTER_CASH".equalsIgnoreCase(paymentMethod);
    }

    private BigDecimal sumByType(List<CashSessionTransaction> txns, String type) {
        return txns.stream()
                .filter(t -> type.equals(t.getType()))
                .map(t -> nvl(t.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumByTypes(List<CashSessionTransaction> txns, String... types) {
        java.util.Set<String> included = java.util.Set.of(types);
        return txns.stream()
                .filter(t -> included.contains(t.getType()))
                .map(t -> nvl(t.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumByTypeAndMethod(List<CashSessionTransaction> txns, String type, String method) {
        return txns.stream()
                .filter(t -> type.equals(t.getType()) && method.equals(t.getPaymentMethod()))
                .map(t -> nvl(t.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumByTypeAndCashMethod(List<CashSessionTransaction> txns, String type) {
        return txns.stream()
                .filter(t -> type.equals(t.getType()) && isCashPayment(t.getPaymentMethod()))
                .map(t -> nvl(t.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumByTypeNotMethod(List<CashSessionTransaction> txns, String type, String... excludeMethods) {
        java.util.Set<String> excluded = java.util.Set.of(excludeMethods);
        return txns.stream()
                .filter(t -> type.equals(t.getType()) && !excluded.contains(t.getPaymentMethod()))
                .map(t -> nvl(t.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SessionResponse buildSessionResponse(CashSession session) {
        List<DenominationDto> opening = denominationRepository
                .findBySessionIdAndType(session.getSessionId(), "OPENING")
                .stream().map(this::mapDenomination).toList();

        List<DenominationDto> closing = denominationRepository
                .findBySessionIdAndType(session.getSessionId(), "CLOSING")
                .stream().map(this::mapDenomination).toList();

        return SessionResponse.builder()
                .sessionId(session.getSessionId())
                .counterId(session.getCounterId())
                .openedBy(session.getOpenedBy())
                .openedAt(session.getOpenedAt())
                .openingCash(session.getOpeningCash())
                .closedBy(session.getClosedBy())
                .closedAt(session.getClosedAt())
                .closingCash(session.getClosingCash())
                .expectedCash(session.getExpectedCash())
                .cashDifference(session.getCashDifference())
                .sessionType(session.getSessionType())
                .purpose(session.getPurpose())
                .status(session.getStatus())
                .openingDenominations(opening)
                .closingDenominations(closing)
                .build();
    }

    private Counter findCounterById(Long counterId) {
        return counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found: " + counterId));
    }

    private CashSession findSessionById(Long sessionId) {
        return cashSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Cash session not found: " + sessionId));
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String clean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    // ─── Mappers ─────────────────────────────────────────────────────────────────

    private CounterResponse mapCounter(Counter c) {
        return CounterResponse.builder()
                .counterId(c.getCounterId())
                .branchId(c.getBranchId())
                .name(c.getName())
                .location(c.getLocation())
                .isActive(c.getIsActive())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private DenominationDto mapDenomination(CashSessionDenomination d) {
        return DenominationDto.builder()
                .denomination(d.getDenomination())
                .qty(d.getQty())
                .total(d.getTotal())
                .build();
    }

    private CashSessionTransactionResponse mapTransaction(CashSessionTransaction t) {
        return CashSessionTransactionResponse.builder()
                .id(t.getId())
                .sessionId(t.getSessionId())
                .type(t.getType())
                .amount(t.getAmount())
                .paymentMethod(t.getPaymentMethod())
                .paymentId(t.getPaymentId())
                .supplierPaymentId(t.getSupplierPaymentId())
                .purchaseReturnId(t.getPurchaseReturnId())
                .supplierId(t.getSupplierId())
                .supplyId(t.getSupplyId())
                .counterId(t.getCounterId())
                .referenceNo(t.getReferenceNo())
                .expenseId(t.getExpenseId())
                .withdrawalId(t.getWithdrawalId())
                .orderId(t.getOrderId())
                .invoiceNo(t.getInvoiceNo())
                .salesReturnId(t.getSalesReturnId())
                .note(t.getNote())
                .createdBy(t.getCreatedBy())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private ExpenseResponse mapExpense(Expense e) {
        return ExpenseResponse.builder()
                .expenseId(e.getExpenseId())
                .branchId(e.getBranchId())
                .cashSessionId(e.getCashSessionId())
                .category(e.getCategory())
                .amount(e.getAmount())
                .paymentMethod(e.getPaymentMethod())
                .expenseDate(e.getExpenseDate())
                .createdBy(e.getCreatedBy())
                .note(e.getNote())
                .build();
    }

    private WithdrawalResponse mapWithdrawal(Withdrawal w) {
        return WithdrawalResponse.builder()
                .withdrawalId(w.getWithdrawalId())
                .cashSessionId(w.getCashSessionId())
                .amount(w.getAmount())
                .reason(w.getReason())
                .notes(w.getNotes())
                .withdrawalDate(w.getWithdrawalDate())
                .userId(w.getUserId())
                .build();
    }
}
