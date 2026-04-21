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
@Table(name = "scale_barcode_settings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "prefix"})
})
public class ScaleBarcodeSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long settingId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 10)
    private String prefix;

    @Column(nullable = false)
    private Integer totalLength;

    @Column(nullable = false)
    private Integer itemCodeStart;

    @Column(nullable = false)
    private Integer itemCodeLength;

    // WEIGHT or PRICE
    @Column(nullable = false, length = 20)
    private String valueType;

    @Column(nullable = false)
    private Integer valueStart;

    @Column(nullable = false)
    private Integer valueLength;

    @Column(nullable = false)
    private Integer valueDecimalPlaces;

    private Boolean isActive = true;
    private LocalDateTime createdAt;
}