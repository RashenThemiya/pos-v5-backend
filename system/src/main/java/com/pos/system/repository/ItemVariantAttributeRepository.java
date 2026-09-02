package com.pos.system.repository;

import com.pos.system.model.catalog.ItemVariantAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ItemVariantAttributeRepository extends JpaRepository<ItemVariantAttribute, Long> {
    List<ItemVariantAttribute> findByVariantIdOrderByAttributeNameAsc(Long variantId);
    List<ItemVariantAttribute> findByVariantIdIn(Collection<Long> variantIds);
    void deleteByVariantId(Long variantId);
    void deleteByVariantIdIn(Collection<Long> variantIds);
}
