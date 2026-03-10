package com.pos.system.model.cash;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "counters", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "name"})
})
public class Counter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long counterId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 150)
    private String location;

    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
