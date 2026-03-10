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
@Table(name = "supplies")
public class Supply {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long supplyId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long supplierId;

    private Long poId;

    @Column(unique = true, length = 50)
    private String grnNo;

    @Column(length = 50)
    private String invoiceNo;

    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal rounding = BigDecimal.ZERO;
    private BigDecimal total;
    private BigDecimal paidAmount;
    private String paymentMethod;
    private String status;
    private LocalDateTime supplyDate;

    @Column(nullable = false)
    private Long receivedBy;

    private String notes;
}
