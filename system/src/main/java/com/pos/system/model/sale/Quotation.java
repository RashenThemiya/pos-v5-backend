package com.pos.system.model.sale;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "quotations", indexes = {
        @Index(name = "idx_quotation_branch_created", columnList = "branchId,createdAt"),
        @Index(name = "idx_quotation_customer", columnList = "customerId")
})
public class Quotation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long quotationId;

    @Column(nullable = false, unique = true, length = 50)
    private String quotationNo;
    @Column(nullable = false)
    private Long branchId;
    @Column(nullable = false)
    private Long userId;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String customerAddress;
    @Column(nullable = false, length = 20)
    private String status;
    private LocalDate validUntil;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal itemDiscount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal discount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal rounding;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;
    @Column(length = 2000)
    private String notes;
    @Column(length = 2000)
    private String terms;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
