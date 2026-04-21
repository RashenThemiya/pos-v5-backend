package com.pos.system.service;

import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.repository.ItemUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UnitConversionServiceImpl implements UnitConversionService {

    private final ItemUnitRepository itemUnitRepository;

    @Override
    public BigDecimal getMultiplierToBase(Long itemId, Long unitId) {
        ItemUnit itemUnit = itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(itemId, unitId)
                .orElseThrow(() -> new RuntimeException(
                        "Active unit not found for itemId=" + itemId + " and unitId=" + unitId
                ));

        if (itemUnit.getMultiplierToBase() == null || itemUnit.getMultiplierToBase().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid multiplierToBase for unitId=" + unitId);
        }

        return itemUnit.getMultiplierToBase();
    }

    @Override
    public BigDecimal toBaseQty(Long itemId, Long unitId, BigDecimal qty) {
        if (qty == null) {
            throw new RuntimeException("Quantity cannot be null");
        }
        return qty.multiply(getMultiplierToBase(itemId, unitId));
    }
}