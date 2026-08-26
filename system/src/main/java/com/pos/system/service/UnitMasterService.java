package com.pos.system.service;

import com.pos.system.dto.unit.UnitRequest;
import com.pos.system.dto.unit.UnitResponse;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.UnitMaster;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.ItemUnitRepository;
import com.pos.system.repository.UnitMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnitMasterService {

    private final UnitMasterRepository unitMasterRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final BranchRepository branchRepository;

    public UnitResponse create(UnitRequest request) {
        validateRequest(request);
        String name = normalizeName(request.getName());

        if (unitMasterRepository.existsByBranchIdAndName(request.getBranchId(), name)) {
            throw new RuntimeException("Unit name already exists in this branch");
        }

        UnitMaster unit = new UnitMaster();
        unit.setBranchId(request.getBranchId());
        unit.setName(name);
        unit.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        unit.setCreatedAt(LocalDateTime.now());
        unit.setUpdatedAt(LocalDateTime.now());

        UnitMaster saved = unitMasterRepository.save(unit);
        syncAssignedItemUnitNames(saved);
        return toResponse(saved);
    }

    public List<UnitResponse> getAllByBranch(Long branchId) {
        validateBranch(branchId);
        return unitMasterRepository.findByBranchId(branchId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<UnitResponse> getActiveByBranch(Long branchId) {
        validateBranch(branchId);
        return unitMasterRepository.findByBranchIdAndIsActive(branchId, true)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UnitResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public UnitResponse update(Long id, UnitRequest request) {
        UnitMaster unit = findById(id);
        String name = normalizeName(request.getName());

        if (!unit.getName().equals(name)
                && unitMasterRepository.existsByBranchIdAndNameAndUnitIdNot(unit.getBranchId(), name, unit.getUnitId())) {
            throw new RuntimeException("Unit name already exists in this branch");
        }

        unit.setName(name);
        if (request.getIsActive() != null) {
            unit.setIsActive(request.getIsActive());
        }
        unit.setUpdatedAt(LocalDateTime.now());

        return toResponse(unitMasterRepository.save(unit));
    }

    public UnitResponse toggleActive(Long id) {
        UnitMaster unit = findById(id);
        unit.setIsActive(!unit.getIsActive());
        unit.setUpdatedAt(LocalDateTime.now());
        return toResponse(unitMasterRepository.save(unit));
    }

    private void validateRequest(UnitRequest request) {
        if (request == null) {
            throw new RuntimeException("Unit request is required");
        }

        validateBranch(request.getBranchId());
        normalizeName(request.getName());
    }

    private void validateBranch(Long branchId) {
        if (branchId == null) {
            throw new RuntimeException("Branch ID is required");
        }

        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
    }

    private UnitMaster findById(Long id) {
        return unitMasterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Unit not found with id: " + id));
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Unit name is required");
        }

        return name.trim().toUpperCase();
    }

    private UnitResponse toResponse(UnitMaster unit) {
        UnitResponse response = new UnitResponse();
        response.setUnitId(unit.getUnitId());
        response.setBranchId(unit.getBranchId());
        response.setName(unit.getName());
        response.setIsActive(unit.getIsActive());
        response.setCreatedAt(unit.getCreatedAt());
        response.setUpdatedAt(unit.getUpdatedAt());
        return response;
    }

    private void syncAssignedItemUnitNames(UnitMaster unit) {
        List<ItemUnit> itemUnits = itemUnitRepository.findByMasterUnitId(unit.getUnitId());
        itemUnits.forEach(itemUnit -> {
            itemUnit.setUnitName(unit.getName());
            itemUnit.setUpdatedAt(LocalDateTime.now());
        });
        itemUnitRepository.saveAll(itemUnits);
    }
}
