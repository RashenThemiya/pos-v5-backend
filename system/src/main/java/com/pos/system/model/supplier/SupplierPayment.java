package com.pos.system.model.supplier;

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
@Table(name = "supplier_payments")
public class SupplierPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long supplierPaymentId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long supplierId;

    private Long supplyId;

    @Column(nullable = false)
    private BigDecimal amount;

    private String paymentMethod;
    private LocalDateTime paymentDate;

    @Column(nullable = false)
    private Long paidBy;

    private Long cashSessionId;
    private String referenceNo;
    private String note;
}
