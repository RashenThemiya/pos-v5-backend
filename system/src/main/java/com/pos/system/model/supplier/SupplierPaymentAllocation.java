package com.pos.system.model.supplier;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "supplier_payment_allocations")
public class SupplierPaymentAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long allocationId;
    @Column(nullable = false)
    private Long supplierPaymentId;
    @Column(nullable = false)
    private Long supplyId;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
