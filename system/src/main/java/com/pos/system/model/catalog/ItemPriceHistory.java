package com.pos.system.model.catalog;

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
@Table(name = "item_price_history")
public class ItemPriceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long priceId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private BigDecimal oldPrice;

    @Column(nullable = false)
    private BigDecimal newPrice;

    private String reason;

    @Column(nullable = false)
    private Long changedBy;

    private LocalDateTime changedAt;
}
