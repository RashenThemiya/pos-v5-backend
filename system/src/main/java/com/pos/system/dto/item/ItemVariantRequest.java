package com.pos.system.dto.item;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ItemVariantRequest {
    private Long variantId;
    private String sku;
    private BigDecimal defaultSellingPrice;
    private String image;
    private MultipartFile imageFile;
    private Boolean isActive;
    private List<ItemVariantAttributeRequest> attributes;
}
