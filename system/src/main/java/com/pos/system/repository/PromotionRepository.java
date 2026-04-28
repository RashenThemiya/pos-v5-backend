package com.pos.system.repository;

import com.pos.system.model.promotion.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findByBranchId(Long branchId);
    List<Promotion> findByBranchIdAndIsActiveTrue(Long branchId);
    Optional<Promotion> findByBranchIdAndPromoCode(Long branchId, String promoCode);

    @Query("SELECT p FROM Promotion p WHERE p.branchId = :branchId AND p.isActive = true " +
           "AND (p.startAt IS NULL OR p.startAt <= :now) " +
           "AND (p.endAt IS NULL OR p.endAt >= :now)")
    List<Promotion> findActiveByBranchIdAndNow(Long branchId, LocalDateTime now);
}
