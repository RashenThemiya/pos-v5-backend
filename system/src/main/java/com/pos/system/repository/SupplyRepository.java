package com.pos.system.repository;

import com.pos.system.model.supplier.Supply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplyRepository extends JpaRepository<Supply, Long> {
    List<Supply> findByBranchId(Long branchId);
    Optional<Supply> findByGrnNo(String grnNo);
}