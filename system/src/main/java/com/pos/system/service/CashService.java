package com.pos.system.service;

import com.pos.system.dto.cash.*;

import java.util.List;

public interface CashService {

    // Counter
    CounterResponse createCounter(CounterRequest request);
    CounterResponse updateCounter(Long counterId, CounterRequest request);
    CounterResponse getCounterById(Long counterId);
    List<CounterResponse> getCountersByBranch(Long branchId);

    // Session
    SessionResponse openSession(OpenSessionRequest request);
    SessionResponse closeSession(Long sessionId, CloseSessionRequest request);
    SessionResponse getSessionById(Long sessionId);
    SessionResponse getActiveSessionByCounter(Long counterId);
    List<SessionResponse> getSessionsByCounter(Long counterId);

    // Session summary
    SessionSummaryResponse getSessionSummary(Long sessionId);

    // Transactions
    List<CashSessionTransactionResponse> getTransactionsBySession(Long sessionId);

    // Expenses
    ExpenseResponse addExpense(ExpenseRequest request);
    List<ExpenseResponse> getExpensesBySession(Long sessionId);
    List<ExpenseResponse> getExpensesByBranch(Long branchId);

    // Withdrawals
    WithdrawalResponse addWithdrawal(WithdrawalRequest request);
    List<WithdrawalResponse> getWithdrawalsBySession(Long sessionId);
}
