package com.pos.system.dto.item;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CatalogImportResponse {
    private Long branchId;
    private int categoriesCreated;
    private int brandsCreated;
    private int itemsCreated;
    private int itemsUpdated;
    private int unitsCreated;
    private int variantsCreated;
    private int variantsUpdated;
    private int imagesUploaded;
    private int skippedRows;
    private List<String> warnings;
}
