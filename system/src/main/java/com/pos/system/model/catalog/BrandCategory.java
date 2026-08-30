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
@Table(name = "brand_categories", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "brand_id", "category_id"})
})
public class BrandCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long brandCategoryId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private Long categoryId;

    private LocalDateTime createdAt;
}
