package com.pos.system.repository;

import com.pos.system.model.cash.CashSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashSessionRepository extends JpaRepository<CashSession, Long> {
    Optional<CashSession> findByCounterIdAndStatus(Long counterId, String status);
    Optional<CashSession> findTopByCounterIdAndStatusOrderByOpenedAtDesc(Long counterId, String status);
    List<CashSession> findByCounterIdOrderByOpenedAtDesc(Long counterId);
}
