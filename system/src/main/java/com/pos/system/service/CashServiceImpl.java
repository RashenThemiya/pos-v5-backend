package com.pos.system.service;

import com.pos.system.dto.cash.*;
import com.pos.system.model.cash.*;
import com.pos.system.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        session.setStatus("OPEN");

        CashSession saved = cashSessionRepository.save(session);

        saveDenominations(saved.getSessionId(), "OPENING", request.getDenominations());

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

        BigDecimal cashSales     = sumByTypeAndMethod(txns, "SALE", "CASH");
        BigDecimal cardSales     = sumByTypeAndMethod(txns, "SALE", "CARD");
        BigDecimal otherSales    = sumByTypeNotMethod(txns, "SALE", "CASH", "CARD");
        BigDecimal expenses      = sumByType(txns, "EXPENSE");
        BigDecimal withdrawals   = sumByType(txns, "WITHDRAWAL");
        BigDecimal supplierPays  = sumByType(txns, "SUPPLIER_PAYMENT");

        BigDecimal expected = nvl(session.getOpeningCash())
                .add(cashSales)
                .subtract(expenses)
                .subtract(withdrawals)
                .subtract(supplierPays);

        return SessionSummaryResponse.builder()
                .sessionId(sessionId)
                .openingCash(session.getOpeningCash())
                .totalCashSales(cashSales)
                .totalCardSales(cardSales)
                .totalOtherSales(otherSales)
                .totalExpenses(expenses)
                .totalWithdrawals(withdrawals)
                .totalSupplierPayments(supplierPays)
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

        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setCashSessionId(request.getCashSessionId());
        withdrawal.setAmount(nvl(request.getAmount()));
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
        txn.setCreatedBy(createdBy);
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
        BigDecimal cashIn  = sumByTypeAndMethod(txns, "SALE", "CASH");
        BigDecimal expenses = sumByType(txns, "EXPENSE");
        BigDecimal withdrawals = sumByType(txns, "WITHDRAWAL");
        BigDecimal supplierPayments = sumByType(txns, "SUPPLIER_PAYMENT");
        return nvl(openingCash).add(cashIn).subtract(expenses).subtract(withdrawals).subtract(supplierPayments);
    }

    private BigDecimal sumByType(List<CashSessionTransaction> txns, String type) {
        return txns.stream()
                .filter(t -> type.equals(t.getType()))
                .map(t -> nvl(t.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumByTypeAndMethod(List<CashSessionTransaction> txns, String type, String method) {
        return txns.stream()
                .filter(t -> type.equals(t.getType()) && method.equals(t.getPaymentMethod()))
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
                .expenseId(t.getExpenseId())
                .withdrawalId(t.getWithdrawalId())
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
