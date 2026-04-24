package com.pos.system.dto.cash;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CloseSessionRequest {
    private Long closedBy;
    private BigDecimal closingCash;
    private List<DenominationDto> denominations;
    private String note;
}
