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
@Table(name = "scale_item_mappings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "scale_item_code"})
})
public class ScaleItemMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 30)
    private String scaleItemCode;

    @Column(nullable = false)
    private Long itemId;

    // usually KG / L base unit for weighed item
    @Column(nullable = false)
    private Long unitId;

    private Boolean isActive = true;
    private LocalDateTime createdAt;
}