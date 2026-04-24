package com.pos.system.dto.cash;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OpenSessionRequest {
    private Long counterId;
    private Long openedBy;
    private BigDecimal openingCash;
    private List<DenominationDto> denominations;
}
