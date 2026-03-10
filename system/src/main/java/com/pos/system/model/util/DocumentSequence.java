package com.pos.system.model.util;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "document_sequences", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "doc_type"})
})
public class DocumentSequence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sequenceId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 30)
    private String docType;

    @Column(nullable = false, length = 20)
    private String prefix;

    @Column(nullable = false)
    private Long currentNo;

    private String resetRule;
    private LocalDateTime updatedAt;
}
