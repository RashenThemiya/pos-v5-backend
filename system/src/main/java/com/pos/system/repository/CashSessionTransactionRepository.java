package com.pos.system.repository;

import com.pos.system.model.cash.CashSessionTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CashSessionTransactionRepository extends JpaRepository<CashSessionTransaction, Long> {
    List<CashSessionTransaction> findBySessionIdOrderByCreatedAtDesc(Long sessionId);
    List<CashSessionTransaction> findBySessionIdAndType(Long sessionId, String type);

    @Query("SELECT t FROM CashSessionTransaction t JOIN CashSession s ON s.sessionId = t.sessionId " +
            "WHERE s.counterId IN :counterIds " +
            "AND t.createdAt BETWEEN :from AND :to " +
            "ORDER BY t.createdAt DESC")
    List<CashSessionTransaction> findByCounterIdsAndCreatedAtBetween(
            @Param("counterIds") Collection<Long> counterIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
