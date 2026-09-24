package com.pos.system.model.sale;

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
@Table(name = "return_voucher_transactions", indexes = {
        @Index(name = "idx_return_voucher_txn_voucher", columnList = "voucher_id"),
        @Index(name = "idx_return_voucher_txn_order", columnList = "order_id")
})
public class ReturnVoucherTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @Column(nullable = false)
    private Long voucherId;

    @Column(nullable = false, length = 60)
    private String voucherNo;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceAfter;

    private Long orderId;
    private Long salesReturnId;
    private String referenceNo;
    private String note;

    @Column(nullable = false)
    private Long createdBy;

    private LocalDateTime createdAt;
}
