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
@Table(name = "credit_notes", uniqueConstraints = {
        @UniqueConstraint(columnNames = "credit_note_no")
})
public class CreditNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long creditNoteId;

    @Column(nullable = false, length = 60)
    private String creditNoteNo;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long returnId;

    private Long customerId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal usedAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal remainingAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 30)
    private String settlementMethod;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false)
    private Long createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
