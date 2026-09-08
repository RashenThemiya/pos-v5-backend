package com.pos.system.dto.cash;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {
    private Long sessionId;
    private Long counterId;
    private Long openedBy;
    private LocalDateTime openedAt;
    private BigDecimal openingCash;
    private Long closedBy;
    private LocalDateTime closedAt;
    private BigDecimal closingCash;
    private BigDecimal expectedCash;
    private BigDecimal cashDifference;
    private String sessionType;
    private String purpose;
    private String status;
    private List<DenominationDto> openingDenominations;
    private List<DenominationDto> closingDenominations;
}
