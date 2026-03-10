package com.pos.system.model.promotion;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "promotion_redemptions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"promotion_id", "order_id"})
})
public class PromotionRedemption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long redemptionId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long promotionId;

    @Column(nullable = false)
    private Long orderId;

    private Long customerId;
    private BigDecimal discountAmount;
    private LocalDateTime usedAt;
}
