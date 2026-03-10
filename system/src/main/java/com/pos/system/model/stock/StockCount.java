package com.pos.system.model.stock;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_counts")
public class StockCount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockCountId;

    @Column(nullable = false)
    private Long branchId;

    private LocalDateTime countDate;
    private String status;

    @Column(nullable = false)
    private Long createdBy;

    private Long approvedBy;
    private String note;
}
