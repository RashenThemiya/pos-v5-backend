package com.pos.system.model.catalog;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "brands", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "name"})
})
public class Brand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long brandId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 100)
    private String name;

    private Boolean isActive = true;
    private LocalDateTime createdAt;
}
