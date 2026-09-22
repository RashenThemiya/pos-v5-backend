package com.pos.system.repository;

import com.pos.system.model.sale.SalesReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {
    List<SalesReturn> findByOrderId(Long orderId);
    List<SalesReturn> findByBranchIdOrderByReturnDateDesc(Long branchId);
    boolean existsByReturnNo(String returnNo);
}
