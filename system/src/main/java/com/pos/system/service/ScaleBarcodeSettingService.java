package com.pos.system.service;

import com.pos.system.dto.scale.ScaleBarcodeSettingRequest;
import com.pos.system.dto.scale.ScaleBarcodeSettingResponse;
import com.pos.system.model.catalog.ScaleBarcodeSetting;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.ScaleBarcodeSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScaleBarcodeSettingService {

    private final ScaleBarcodeSettingRepository scaleBarcodeSettingRepository;
    private final BranchRepository branchRepository;

    public ScaleBarcodeSettingResponse create(ScaleBarcodeSettingRequest request) {
        validateBranch(request.getBranchId());
        validateRequest(request, null);

        ScaleBarcodeSetting setting = new ScaleBarcodeSetting();
        setting.setBranchId(request.getBranchId());
        setting.setPrefix(normalizePrefix(request.getPrefix()));
        setting.setTotalLength(request.getTotalLength());
        setting.setItemCodeStart(request.getItemCodeStart());
        setting.setItemCodeLength(request.getItemCodeLength());
        setting.setValueType(normalizeValueType(request.getValueType()));
        setting.setValueStart(request.getValueStart());
        setting.setValueLength(request.getValueLength());
        setting.setValueDecimalPlaces(request.getValueDecimalPlaces());
        setting.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        setting.setCreatedAt(LocalDateTime.now());

        return toResponse(scaleBarcodeSettingRepository.save(setting));
    }

    public List<ScaleBarcodeSettingResponse> getByBranch(Long branchId) {
        validateBranch(branchId);
        return scaleBarcodeSettingRepository.findByBranchId(branchId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ScaleBarcodeSettingResponse getById(Long id) {
        return toResponse(findById(id));
    }

    private void validateRequest(ScaleBarcodeSettingRequest request, Long existingSettingId) {
        String prefix = normalizePrefix(request.getPrefix());

        if (scaleBarcodeSettingRepository.existsByBranchIdAndPrefix(request.getBranchId(), prefix)) {
            throw new RuntimeException("Scale barcode prefix already exists in this branch: " + prefix);
        }

        String valueType = normalizeValueType(request.getValueType());
        if (!"WEIGHT".equals(valueType) && !"PRICE".equals(valueType)) {
            throw new RuntimeException("Value type must be WEIGHT or PRICE");
        }

        validateRange("Item code", request.getTotalLength(), request.getItemCodeStart(), request.getItemCodeLength());
        validateRange("Value", request.getTotalLength(), request.getValueStart(), request.getValueLength());

        int prefixEnd = prefix.length();
        if (request.getTotalLength() < prefixEnd) {
            throw new RuntimeException("Total length cannot be smaller than prefix length");
        }
    }

    private void validateRange(String name, Integer totalLength, Integer start, Integer length) {
        int end = start + length - 1;
        if (end > totalLength) {
            throw new RuntimeException(name + " range exceeds total barcode length");
        }
    }

    private void validateBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
    }

    private ScaleBarcodeSetting findById(Long id) {
        return scaleBarcodeSettingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Scale barcode setting not found with id: " + id));
    }

    private String normalizePrefix(String prefix) {
        return prefix == null ? null : prefix.trim();
    }

    private String normalizeValueType(String valueType) {
        return valueType == null ? null : valueType.trim().toUpperCase(Locale.ROOT);
    }

    private ScaleBarcodeSettingResponse toResponse(ScaleBarcodeSetting setting) {
        ScaleBarcodeSettingResponse response = new ScaleBarcodeSettingResponse();
        response.setSettingId(setting.getSettingId());
        response.setBranchId(setting.getBranchId());
        response.setPrefix(setting.getPrefix());
        response.setTotalLength(setting.getTotalLength());
        response.setItemCodeStart(setting.getItemCodeStart());
        response.setItemCodeLength(setting.getItemCodeLength());
        response.setValueType(setting.getValueType());
        response.setValueStart(setting.getValueStart());
        response.setValueLength(setting.getValueLength());
        response.setValueDecimalPlaces(setting.getValueDecimalPlaces());
        response.setIsActive(setting.getIsActive());
        response.setCreatedAt(setting.getCreatedAt());
        return response;
    }
}
