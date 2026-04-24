package com.pos.system.repository;

import com.pos.system.model.cash.CashSessionDenomination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashSessionDenominationRepository extends JpaRepository<CashSessionDenomination, Long> {
    List<CashSessionDenomination> findBySessionIdAndType(Long sessionId, String type);
}
