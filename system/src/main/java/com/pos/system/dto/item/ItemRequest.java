package com.pos.system.dto.item;

import java.math.BigDecimal;

import org.springframework.web.multipart.MultipartFile;

import lombok.Data;

@Data
public class ItemRequest {
    private Long branchId;
    private String sku;
    private String name;
    private String image;
    private Long categoryId;
    private Long brandId;
    private Boolean isWeighed;
    private String scaleBarcodePrefix;
    private Boolean isActive;
    private String baseUnitName;
    private String baseUnitMultiplierToBase;
    private String baseUnitBarcode;
    private String baseUnitDefaultSellingPrice;
    private Boolean baseUnitIsActive;
    private ItemUnitRequest baseUnit;
    private String scaleItemCode;
    private Boolean autoGenerateBarcode;
      private MultipartFile imageFile;
      private BigDecimal minStock;
private BigDecimal maxStock;
}