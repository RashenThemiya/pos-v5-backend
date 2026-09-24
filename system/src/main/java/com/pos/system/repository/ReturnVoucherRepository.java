package com.pos.system.repository;

import com.pos.system.model.sale.ReturnVoucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReturnVoucherRepository extends JpaRepository<ReturnVoucher, Long> {
    boolean existsByVoucherNo(String voucherNo);
    boolean existsByRedemptionCode(String redemptionCode);
    Optional<ReturnVoucher> findByVoucherNo(String voucherNo);
    Optional<ReturnVoucher> findByRedemptionCode(String redemptionCode);
    List<ReturnVoucher> findByOriginalOrderIdOrderByCreatedAtDesc(Long originalOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ReturnVoucher v where v.voucherNo = :code or v.redemptionCode = :code")
    Optional<ReturnVoucher> findRedeemableByCodeForUpdate(@Param("code") String code);
}
