package com.pos.system.repository;

import com.pos.system.model.sale.SalesReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {
    List<SalesReturn> findByOrderId(Long orderId);
    List<SalesReturn> findByBranchIdOrderByReturnDateDesc(Long branchId);
    List<SalesReturn> findByBranchIdAndReturnDateBetweenOrderByReturnDateDesc(
            Long branchId, LocalDateTime from, LocalDateTime to);
    Page<SalesReturn> findByBranchId(Long branchId, Pageable pageable);
}
