package com.pos.system.service;

import com.pos.system.dto.brand.BrandRequest;
import com.pos.system.dto.brand.BrandResponse;
import com.pos.system.model.catalog.Brand;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final BranchRepository branchRepository;

    public BrandResponse create(BrandRequest request) {
        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + request.getBranchId()));

        if (brandRepository.existsByBranchIdAndName(request.getBranchId(), request.getName())) {
            throw new RuntimeException("Brand name already exists in this branch");
        }

        Brand brand = new Brand();
        brand.setBranchId(request.getBranchId());
        brand.setName(request.getName());
        brand.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        brand.setCreatedAt(LocalDateTime.now());

        return toResponse(brandRepository.save(brand));
    }

    public List<BrandResponse> getAllByBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        return brandRepository.findByBranchId(branchId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<BrandResponse> getActiveByBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        return brandRepository.findByBranchIdAndIsActive(branchId, true)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public BrandResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public BrandResponse update(Long id, BrandRequest request) {
        Brand brand = findById(id);

        if (!brand.getName().equals(request.getName()) &&
                brandRepository.existsByBranchIdAndName(brand.getBranchId(), request.getName())) {
            throw new RuntimeException("Brand name already exists in this branch");
        }

        brand.setName(request.getName());
        if (request.getIsActive() != null) {
            brand.setIsActive(request.getIsActive());
        }

        return toResponse(brandRepository.save(brand));
    }

    public BrandResponse toggleActive(Long id) {
        Brand brand = findById(id);
        brand.setIsActive(!brand.getIsActive());
        return toResponse(brandRepository.save(brand));
    }

    public void delete(Long id) {
        Brand brand = findById(id);
        brandRepository.delete(brand);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Brand findById(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Brand not found with id: " + id));
    }

    private BrandResponse toResponse(Brand brand) {
        BrandResponse response = new BrandResponse();
        response.setBrandId(brand.getBrandId());
        response.setBranchId(brand.getBranchId());
        response.setName(brand.getName());
        response.setIsActive(brand.getIsActive());
        response.setCreatedAt(brand.getCreatedAt());
        return response;
    }
}
