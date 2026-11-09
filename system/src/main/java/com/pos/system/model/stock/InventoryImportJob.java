package com.pos.system.model.stock;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "inventory_import_jobs", uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "file_hash"}))
public class InventoryImportJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long branchId;
    @Column(nullable = false, length = 64) private String fileHash;
    private String fileName;
    private String mode;
    private String status;
    private String uploadedBy;
    private String postedBy;
    private LocalDateTime createdAt;
    private LocalDateTime postedAt;
    @Lob @Column(columnDefinition = "LONGTEXT") private String payload;
    @Lob @Column(columnDefinition = "LONGTEXT") private String errors;
    private Integer rowCount;
}
