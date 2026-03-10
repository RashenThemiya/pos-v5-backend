package com.pos.system.model.util;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "branch_settings")
public class BranchSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long settingId;

    @Column(nullable = false, unique = true)
    private Long branchId;

    private Boolean allowNegativeStock = false;
    private Boolean strictFifo = true;
    private Boolean priceIncludesTax = false;
    private Boolean allowReturnWithoutInvoice = false;
    private Boolean defaultCustomerRequired = false;
    private Boolean allowManualPriceEdit = false;
    private String currencyCode = "LKR";
    private LocalDateTime updatedAt;
}
