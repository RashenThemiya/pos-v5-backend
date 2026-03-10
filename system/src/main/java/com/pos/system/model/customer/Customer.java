package com.pos.system.model.customer;

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
@Table(name = "customers", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "loyalty_card_no"})
})
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long customerId;

    @Column(nullable = false)
    private Long branchId;

    @Column(unique = true)
    private Long authId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 50)
    private String nic;

    private String nicImageFront;
    private String nicImageBack;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    private BigDecimal shopBalance = BigDecimal.ZERO;
    private String loyaltyCardNo;
    private BigDecimal loyaltyPoints = BigDecimal.ZERO;
    private BigDecimal creditLimit;
    private Boolean isActive = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
