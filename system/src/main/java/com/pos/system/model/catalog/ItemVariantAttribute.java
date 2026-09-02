package com.pos.system.model.catalog;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_variant_attributes", indexes = {
        @Index(name = "idx_item_variant_attributes_variant", columnList = "variant_id"),
        @Index(name = "idx_item_variant_attributes_name_value", columnList = "attribute_name, attribute_value")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"variant_id", "attribute_name"})
})
public class ItemVariantAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attributeId;

    @Column(nullable = false)
    private Long variantId;

    @Column(name = "attribute_name", nullable = false, length = 80)
    private String attributeName;

    @Column(name = "attribute_value", nullable = false, length = 120)
    private String attributeValue;
}
