package com.pos.system.service;

import java.math.BigDecimal;

public interface UnitConversionService {
    BigDecimal getMultiplierToBase(Long itemId, Long unitId);
    BigDecimal toBaseQty(Long itemId, Long unitId, BigDecimal qty);
}