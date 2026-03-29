package com.pos.system.dto.stock;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StockCountResponse {
    private Long stockCountId;
    private Long branchId;
    private LocalDateTime countDate;
    private String status;
    private Long createdBy;
    private Long approvedBy;
    private String note;
    private List<ItemCountDetail> items;

    @Data
    public static class ItemCountDetail {
        private Long stockCountItemId;
        private Long itemId;
        private String itemName;
        private String itemSku;
        private BigDecimal systemQty;
        private BigDecimal countedQty;
        private BigDecimal differenceQty;
        private String note;
    }
}
