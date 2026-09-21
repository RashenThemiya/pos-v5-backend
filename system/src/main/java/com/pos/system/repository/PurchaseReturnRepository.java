package com.pos.system.repository;

import com.pos.system.model.supplier.PurchaseReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long>, JpaSpecificationExecutor<PurchaseReturn> {

    List<PurchaseReturn> findByBranchIdOrderByReturnDateDesc(Long branchId);

    List<PurchaseReturn> findByBranchIdAndSupplierIdOrderByReturnDateDesc(
            Long branchId,
            Long supplierId
    );

    List<PurchaseReturn> findBySupplyIdOrderByReturnDateDesc(Long supplyId);

    @Query("select purchaseReturn from PurchaseReturn purchaseReturn where purchaseReturn.supplyId in :supplyIds order by purchaseReturn.returnDate desc")
    List<PurchaseReturn> findBySupplyIdInOrderByReturnDateDesc(@Param("supplyIds") Collection<Long> supplyIds);
}
