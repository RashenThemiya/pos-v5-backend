package com.pos.system.service;

import com.pos.system.dto.brand.*;
import com.pos.system.model.catalog.Brand;
import com.pos.system.model.catalog.BrandCategory;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.BrandCategoryRepository;
import com.pos.system.repository.BrandRepository;
import com.pos.system.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final BrandCategoryRepository brandCategoryRepository;
    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
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

        Brand saved = brandRepository.save(brand);
        if (request.getCategoryIds() != null) {
            syncCategoryMappings(saved, request.getCategoryIds());
        }

        return toResponse(saved);
    }

    public List<BrandResponse> getAllByBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        return brandRepository.findByBranchId(branchId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public BrandPageResponseDto searchByBranch(Long branchId, BrandSearchRequestDto request, Pageable pageable) {
        BrandSearchRequestDto filters = request != null ? request : new BrandSearchRequestDto();
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));

        Specification<Brand> specification = (root, query, cb) -> cb.equal(root.get("branchId"), branchId);

        if (filters.getActive() != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("isActive"), filters.getActive()));
        }

        if (StringUtils.hasText(filters.getQ())) {
            String pattern = "%" + filters.getQ().trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("brandId").as(String.class)), pattern)
            ));
        }

        Page<Brand> page = brandRepository.findAll(specification, pageable);
        List<Brand> allBranchBrands = brandRepository.findByBranchId(branchId);

        return BrandPageResponseDto.builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .sort(formatPageSort(pageable))
                .summary(buildBrandSummary(allBranchBrands))
                .build();
    }

    public List<BrandResponse> getActiveByBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        return brandRepository.findByBranchIdAndIsActive(branchId, true)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<BrandResponse> getActiveByBranchAndCategory(Long branchId, Long categoryId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        categoryRepository.findByCategoryIdAndBranchId(categoryId, branchId)
                .orElseThrow(() -> new RuntimeException("Category not found in this branch"));

        return brandRepository.findActiveByBranchIdAndCategoryId(branchId, categoryId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public BrandResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Transactional
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

        Brand saved = brandRepository.save(brand);
        if (request.getCategoryIds() != null) {
            syncCategoryMappings(saved, request.getCategoryIds());
        }

        return toResponse(saved);
    }

    public BrandResponse toggleActive(Long id) {
        Brand brand = findById(id);
        brand.setIsActive(!brand.getIsActive());
        return toResponse(brandRepository.save(brand));
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = findById(id);
        brandCategoryRepository.deleteByBrandId(brand.getBrandId());
        brandRepository.delete(brand);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Brand findById(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Brand not found with id: " + id));
    }

    private void syncCategoryMappings(Brand brand, Set<Long> requestedCategoryIds) {
        Set<Long> categoryIds = requestedCategoryIds == null
                ? Set.of()
                : requestedCategoryIds.stream()
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (categoryIds.isEmpty()) {
            throw new RuntimeException("Select at least one category for this brand");
        }

        validateCategoriesForBranch(brand.getBranchId(), categoryIds);

        List<BrandCategory> existing = brandCategoryRepository.findByBrandId(brand.getBrandId());
        Set<Long> existingCategoryIds = existing.stream()
                .map(BrandCategory::getCategoryId)
                .collect(Collectors.toSet());

        List<BrandCategory> toDelete = existing.stream()
                .filter(mapping -> !categoryIds.contains(mapping.getCategoryId()))
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            brandCategoryRepository.deleteAll(toDelete);
        }

        List<BrandCategory> toCreate = new ArrayList<>();
        categoryIds.stream()
                .filter(categoryId -> !existingCategoryIds.contains(categoryId))
                .forEach(categoryId -> {
                    BrandCategory mapping = new BrandCategory();
                    mapping.setBranchId(brand.getBranchId());
                    mapping.setBrandId(brand.getBrandId());
                    mapping.setCategoryId(categoryId);
                    mapping.setCreatedAt(LocalDateTime.now());
                    toCreate.add(mapping);
                });

        if (!toCreate.isEmpty()) {
            brandCategoryRepository.saveAll(toCreate);
        }
    }

    private void validateCategoriesForBranch(Long branchId, Set<Long> categoryIds) {
        categoryIds.forEach(categoryId ->
                categoryRepository.findByCategoryIdAndBranchId(categoryId, branchId)
                        .orElseThrow(() -> new RuntimeException("Category not found in this branch: " + categoryId))
        );
    }

    private BrandResponse toResponse(Brand brand) {
        BrandResponse response = new BrandResponse();
        response.setBrandId(brand.getBrandId());
        response.setBranchId(brand.getBranchId());
        response.setName(brand.getName());
        response.setIsActive(brand.getIsActive());
        response.setCreatedAt(brand.getCreatedAt());

        List<Long> categoryIds = brandCategoryRepository.findByBrandId(brand.getBrandId())
                .stream()
                .map(BrandCategory::getCategoryId)
                .collect(Collectors.toList());
        response.setCategoryIds(categoryIds);

        Map<Long, String> categoryNames = categoryRepository.findByBranchId(brand.getBranchId())
                .stream()
                .collect(Collectors.toMap(category -> category.getCategoryId(), category -> category.getName()));
        response.setCategoryNames(categoryIds.stream()
                .map(categoryNames::get)
                .filter(name -> name != null)
                .collect(Collectors.toList()));

        return response;
    }

    private BrandSummaryDto buildBrandSummary(List<Brand> brands) {
        return BrandSummaryDto.builder()
                .total(brands.size())
                .active(brands.stream().filter(brand -> Boolean.TRUE.equals(brand.getIsActive())).count())
                .inactive(brands.stream().filter(brand -> !Boolean.TRUE.equals(brand.getIsActive())).count())
                .build();
    }

    private String formatPageSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return "brandId,ASC";
        }
        return String.join(";",
                pageable.getSort().stream()
                        .map(order -> order.getProperty() + "," + order.getDirection().name())
                        .toList());
    }
}
