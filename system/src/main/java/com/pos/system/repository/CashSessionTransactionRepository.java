package com.pos.system.repository;

import com.pos.system.model.cash.CashSessionTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashSessionTransactionRepository extends JpaRepository<CashSessionTransaction, Long> {
    List<CashSessionTransaction> findBySessionIdOrderByCreatedAtDesc(Long sessionId);
    List<CashSessionTransaction> findBySessionIdAndType(Long sessionId, String type);
}
