package com.pos.system.model.sale;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "return_vouchers", indexes = {
        @Index(name = "idx_return_vouchers_order", columnList = "original_order_id"),
        @Index(name = "idx_return_vouchers_return", columnList = "sales_return_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = "voucher_no"),
        @UniqueConstraint(columnNames = "redemption_code")
})
public class ReturnVoucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long voucherId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 60)
    private String voucherNo;

    @Column(nullable = false, length = 80)
    private String redemptionCode;

    @Column(nullable = false)
    private Long originalOrderId;

    @Column(nullable = false)
    private Long salesReturnId;

    @Column(length = 60)
    private String originalInvoiceNo;

    @Column(length = 60)
    private String returnNo;

    @Column(length = 60)
    private String creditNoteNo;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal originalAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal usedAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal remainingAmount;

    @Column(nullable = false, length = 30)
    private String status;

    private LocalDate issueDate;
    private LocalDate expiryDate;

    @Column(nullable = false)
    private Long issuedBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime cancelledAt;
    private Long cancelledBy;
    private String cancelReason;

    @Version
    private Long version;
}
