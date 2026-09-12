package com.pos.system.controller;

import com.pos.system.dto.cash.*;
import com.pos.system.service.CashService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cash")
@RequiredArgsConstructor
@CrossOrigin
public class CashController {

    private final CashService cashService;

    // ─── Counters ────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('COUNTER_CREATE')")
    @PostMapping("/counters")
    public ResponseEntity<CounterResponse> createCounter(@RequestBody CounterRequest request) {
        return ResponseEntity.ok(cashService.createCounter(request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('COUNTER_UPDATE')")
    @PutMapping("/counters/{counterId}")
    public ResponseEntity<CounterResponse> updateCounter(@PathVariable Long counterId,
                                                         @RequestBody CounterRequest request) {
        return ResponseEntity.ok(cashService.updateCounter(counterId, request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('COUNTER_VIEW')")
    @GetMapping("/counters/{counterId}")
    public ResponseEntity<CounterResponse> getCounterById(@PathVariable Long counterId) {
        return ResponseEntity.ok(cashService.getCounterById(counterId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('COUNTER_VIEW')")
    @GetMapping("/counters/branch/{branchId}")
    public ResponseEntity<List<CounterResponse>> getCountersByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(cashService.getCountersByBranch(branchId));
    }

    // ─── Sessions ────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SESSION_OPEN')")
    @PostMapping("/sessions/open")
    public ResponseEntity<SessionResponse> openSession(@RequestBody OpenSessionRequest request) {
        return ResponseEntity.ok(cashService.openSession(request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SESSION_CLOSE')")
    @PutMapping("/sessions/{sessionId}/close")
    public ResponseEntity<SessionResponse> closeSession(@PathVariable Long sessionId,
                                                        @RequestBody CloseSessionRequest request) {
        return ResponseEntity.ok(cashService.closeSession(sessionId, request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SESSION_VIEW')")
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionResponse> getSessionById(@PathVariable Long sessionId) {
        return ResponseEntity.ok(cashService.getSessionById(sessionId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SESSION_VIEW')")
    @GetMapping("/sessions/counter/{counterId}/active")
    public ResponseEntity<SessionResponse> getActiveSessionByCounter(@PathVariable Long counterId) {
        return ResponseEntity.ok(cashService.getActiveSessionByCounter(counterId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SESSION_VIEW')")
    @GetMapping("/sessions/counter/{counterId}/previous")
    public ResponseEntity<PreviousCashSessionResponse> getPreviousSessionByCounter(@PathVariable Long counterId) {
        return ResponseEntity.ok(cashService.getPreviousSessionByCounter(counterId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SESSION_VIEW')")
    @GetMapping("/sessions/counter/{counterId}")
    public ResponseEntity<List<SessionResponse>> getSessionsByCounter(@PathVariable Long counterId) {
        return ResponseEntity.ok(cashService.getSessionsByCounter(counterId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SESSION_VIEW')")
    @GetMapping("/sessions/{sessionId}/summary")
    public ResponseEntity<SessionSummaryResponse> getSessionSummary(@PathVariable Long sessionId) {
        return ResponseEntity.ok(cashService.getSessionSummary(sessionId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('SESSION_VIEW')")
    @GetMapping("/sessions/{sessionId}/transactions")
    public ResponseEntity<List<CashSessionTransactionResponse>> getTransactionsBySession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(cashService.getTransactionsBySession(sessionId));
    }

    // ─── Expenses ────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('EXPENSE_CREATE')")
    @PostMapping("/expenses")
    public ResponseEntity<ExpenseResponse> addExpense(@RequestBody ExpenseRequest request) {
        return ResponseEntity.ok(cashService.addExpense(request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('EXPENSE_VIEW')")
    @GetMapping("/expenses/session/{sessionId}")
    public ResponseEntity<List<ExpenseResponse>> getExpensesBySession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(cashService.getExpensesBySession(sessionId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('EXPENSE_VIEW')")
    @GetMapping("/expenses/branch/{branchId}")
    public ResponseEntity<List<ExpenseResponse>> getExpensesByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(cashService.getExpensesByBranch(branchId));
    }

    // ─── Withdrawals ─────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('WITHDRAWAL_CREATE')")
    @PostMapping("/withdrawals")
    public ResponseEntity<WithdrawalResponse> addWithdrawal(@RequestBody WithdrawalRequest request) {
        return ResponseEntity.ok(cashService.addWithdrawal(request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('WITHDRAWAL_VIEW')")
    @GetMapping("/withdrawals/session/{sessionId}")
    public ResponseEntity<List<WithdrawalResponse>> getWithdrawalsBySession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(cashService.getWithdrawalsBySession(sessionId));
    }
}
