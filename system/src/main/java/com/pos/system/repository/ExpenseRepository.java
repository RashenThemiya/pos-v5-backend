package com.pos.system.repository;

import com.pos.system.model.cash.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByBranchIdOrderByExpenseDateDesc(Long branchId);
    List<Expense> findByCashSessionIdOrderByExpenseDateDesc(Long cashSessionId);
}
