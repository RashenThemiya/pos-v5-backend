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
@Table(name = "scale_barcode_settings")
public class ScaleBarcodeSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long settingId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 10)
    private String prefix;

    private Integer itemCodeStart;
    private Integer itemCodeLength;

    @Column(nullable = false)
    private String valueType; // WEIGHT or PRICE

    private Integer valueStart;
    private Integer valueLength;
    private Integer valueDecimalPlaces;

    private Integer priceStart;
    private Integer priceLength;
    private Integer priceDecimalPlaces;

    private Boolean isActive = true;
    private LocalDateTime createdAt;
}
