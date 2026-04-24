package com.pos.system.repository;

import com.pos.system.model.cash.Withdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {
    List<Withdrawal> findByCashSessionIdOrderByWithdrawalDateDesc(Long cashSessionId);
}
