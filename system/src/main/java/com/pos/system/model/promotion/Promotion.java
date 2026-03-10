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
@Table(name = "promotions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "promo_code"})
})
public class Promotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long promotionId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 50)
    private String promoCode;

    @Column(nullable = false)
    private String type;

    private BigDecimal value;
    private BigDecimal minBillTotal;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer priority = 0;
    private Integer maxUsesTotal;
    private Integer maxUsesPerCustomer;
    private Boolean isStackable = false;
    private Boolean isActive = true;
    @Column(nullable = false)
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
