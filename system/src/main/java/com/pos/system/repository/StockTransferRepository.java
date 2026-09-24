package com.pos.system.repository;

import com.pos.system.model.stock.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    List<StockTransfer> findByFromBranchIdOrderByTransferDateDesc(Long fromBranchId);
    List<StockTransfer> findByToBranchIdOrderByTransferDateDesc(Long toBranchId);
    List<StockTransfer> findByFromBranchIdOrToBranchIdOrderByTransferDateDesc(Long fromBranchId, Long toBranchId);
    Page<StockTransfer> findByFromBranchIdOrToBranchId(Long fromBranchId, Long toBranchId, Pageable pageable);
}
