package com.pos.system.model.supplier;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long poId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long supplierId;

    @Column(nullable = false, unique = true, length = 50)
    private String poNo;

    private String status;
    private LocalDate expectedDate;

    @Column(nullable = false)
    private Long createdBy;

    private LocalDateTime createdAt;
    private String note;
}
