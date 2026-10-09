package com.pos.system.model.cash;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "counter_variance_alert_reads", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "alert_id"})
})
public class CounterVarianceAlertRead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 120)
    private String alertId;

    @Column(nullable = false)
    private LocalDateTime readAt;
}
