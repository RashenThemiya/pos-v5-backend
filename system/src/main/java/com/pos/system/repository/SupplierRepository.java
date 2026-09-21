package com.pos.system.repository;

import com.pos.system.model.supplier.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

    List<Supplier> findByBranchId(Long branchId);

    Optional<Supplier> findByBranchIdAndName(Long branchId, String name);
}
