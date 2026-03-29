package com.pos.system.dto.stock;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StockTransferResponse {
    private Long transferId;
    private Long fromBranchId;
    private String fromBranchName;
    private Long toBranchId;
    private String toBranchName;
    private LocalDateTime transferDate;
    private String status;
    private Long createdBy;
    private String note;
    private List<ItemDetail> items;

    @Data
    public static class ItemDetail {
        private Long transferItemId;
        private Long itemId;
        private String itemName;
        private String itemSku;
        private BigDecimal quantity;
        private String internalBatchBarcode;
    }
}
