package com.pos.system.repository;

import com.pos.system.model.sale.ReturnVoucherTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnVoucherTransactionRepository extends JpaRepository<ReturnVoucherTransaction, Long> {
    List<ReturnVoucherTransaction> findByVoucherIdOrderByCreatedAtDesc(Long voucherId);
}
