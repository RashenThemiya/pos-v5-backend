package com.pos.system.repository;

import com.pos.system.model.sale.SalesReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SalesReturnItemRepository extends JpaRepository<SalesReturnItem, Long> {
    List<SalesReturnItem> findByReturnId(Long returnId);
    List<SalesReturnItem> findByOrderProductId(Long orderProductId);
    boolean existsByVariantId(Long variantId);

    @Query("""
            select coalesce(sum(ri.quantity), 0)
            from SalesReturnItem ri
            join SalesReturn sr on sr.returnId = ri.returnId
            where sr.orderId = :orderId
              and sr.status not in ('CANCELLED', 'REJECTED')
              and (
                    (:orderProductId is not null and ri.orderProductId = :orderProductId)
                    or (
                        :orderProductId is null
                        and ri.itemId = :itemId
                        and ((:variantId is null and ri.variantId is null) or ri.variantId = :variantId)
                        and ((:batchBarcode is null and ri.internalBatchBarcode is null) or ri.internalBatchBarcode = :batchBarcode)
                    )
              )
            """)
    BigDecimal sumReturnedQuantity(
            @Param("orderId") Long orderId,
            @Param("orderProductId") Long orderProductId,
            @Param("itemId") Long itemId,
            @Param("variantId") Long variantId,
            @Param("batchBarcode") String batchBarcode
    );
}
