package com.pos.system.service;

import com.pos.system.dto.category.CategoryRequest;
import com.pos.system.dto.category.CategoryResponse;
import com.pos.system.model.catalog.Category;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BranchRepository branchRepository;

    public CategoryResponse create(CategoryRequest request) {
        // Validate branch exists
        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + request.getBranchId()));

        // Check duplicate name within branch
        if (categoryRepository.existsByBranchIdAndName(request.getBranchId(), request.getName())) {
            throw new RuntimeException("Category name already exists in this branch");
        }

        // Validate parent category if provided
        if (request.getParentId() != null) {
            categoryRepository.findByCategoryIdAndBranchId(request.getParentId(), request.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Parent category not found in this branch"));
        }

        Category category = new Category();
        category.setBranchId(request.getBranchId());
        category.setName(request.getName());
        category.setParentId(request.getParentId());
        category.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        category.setCreatedAt(LocalDateTime.now());

        return toResponse(categoryRepository.save(category));
    }

    public List<CategoryResponse> getAllByBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        return categoryRepository.findByBranchId(branchId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<CategoryResponse> getActiveByBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        return categoryRepository.findByBranchIdAndIsActive(branchId, true)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<CategoryResponse> getRootCategoriesByBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        return categoryRepository.findByBranchIdAndParentId(branchId, null)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<CategoryResponse> getSubCategories(Long branchId, Long parentId) {
        categoryRepository.findByCategoryIdAndBranchId(parentId, branchId)
                .orElseThrow(() -> new RuntimeException("Parent category not found"));
        return categoryRepository.findByBranchIdAndParentId(branchId, parentId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public CategoryResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findById(id);

        // If name changed, check for duplicates
        if (!category.getName().equals(request.getName()) &&
                categoryRepository.existsByBranchIdAndName(category.getBranchId(), request.getName())) {
            throw new RuntimeException("Category name already exists in this branch");
        }

        // Validate parent: cannot set self as parent
        if (request.getParentId() != null && request.getParentId().equals(id)) {
            throw new RuntimeException("Category cannot be its own parent");
        }

        // Validate parent exists in same branch
        if (request.getParentId() != null) {
            categoryRepository.findByCategoryIdAndBranchId(request.getParentId(), category.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Parent category not found in this branch"));
        }

        category.setName(request.getName());
        category.setParentId(request.getParentId());
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }

        return toResponse(categoryRepository.save(category));
    }

    public void delete(Long id) {
        Category category = findById(id);

        // Check for subcategories
        List<Category> subCategories = categoryRepository.findByBranchIdAndParentId(category.getBranchId(), id);
        if (!subCategories.isEmpty()) {
            throw new RuntimeException("Cannot delete category with subcategories. Remove subcategories first.");
        }

        categoryRepository.delete(category);
    }

    public CategoryResponse toggleActive(Long id) {
        Category category = findById(id);
        category.setIsActive(!category.getIsActive());
        return toResponse(categoryRepository.save(category));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
    }

    private CategoryResponse toResponse(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setCategoryId(category.getCategoryId());
        response.setBranchId(category.getBranchId());
        response.setName(category.getName());
        response.setParentId(category.getParentId());
        response.setIsActive(category.getIsActive());
        response.setCreatedAt(category.getCreatedAt());

        // Resolve parent name
        if (category.getParentId() != null) {
            categoryRepository.findById(category.getParentId())
                    .ifPresent(parent -> response.setParentName(parent.getName()));
        }

        return response;
    }
}
