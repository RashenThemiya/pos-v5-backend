package com.pos.system.service;

import com.pos.system.dto.category.*;
import com.pos.system.model.catalog.Category;
import com.pos.system.model.catalog.Item;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.CategoryRepository;
import com.pos.system.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BranchRepository branchRepository;
    private final ItemRepository itemRepository;

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

    public CategoryPageResponseDto searchByBranch(Long branchId, CategorySearchRequestDto request, Pageable pageable) {
        CategorySearchRequestDto filters = request != null ? request : new CategorySearchRequestDto();
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));

        List<Category> categories = categoryRepository.findByBranchId(branchId);
        Map<Long, Long> directProductCounts = buildDirectProductCounts(branchId);
        Map<Long, List<Category>> childrenByParent = buildChildrenByParent(categories);
        String keyword = filters.getQ() != null ? filters.getQ().trim().toLowerCase() : "";

        List<Category> matchingParents = categories.stream()
                .filter(category -> category.getParentId() == null)
                .filter(parent -> matchesCategoryTreeFilters(parent, childrenByParent.getOrDefault(parent.getCategoryId(), List.of()), filters, keyword))
                .sorted(buildCategoryComparator(pageable, directProductCounts, childrenByParent))
                .toList();

        int pageSize = pageable.getPageSize() > 0 ? pageable.getPageSize() : 25;
        int pageNumber = Math.max(pageable.getPageNumber(), 0);
        int fromIndex = Math.min(pageNumber * pageSize, matchingParents.size());
        int toIndex = Math.min(fromIndex + pageSize, matchingParents.size());
        List<Category> pagedParents = matchingParents.subList(fromIndex, toIndex);
        Set<Long> visibleParentIds = pagedParents.stream().map(Category::getCategoryId).collect(Collectors.toSet());

        List<CategoryResponse> content = new ArrayList<>();
        for (Category parent : pagedParents) {
            content.add(toResponse(parent, directProductCounts, childrenByParent));
            childrenByParent.getOrDefault(parent.getCategoryId(), List.of())
                    .forEach(child -> content.add(toResponse(child, directProductCounts, childrenByParent)));
        }

        return CategoryPageResponseDto.builder()
                .content(content)
                .page(pageNumber)
                .pageSize(pageSize)
                .totalElements(matchingParents.size())
                .totalPages(pageSize == 0 ? 0 : (int) Math.ceil((double) matchingParents.size() / pageSize))
                .sort(formatPageSort(pageable))
                .summary(buildCategorySummary(categories, directProductCounts, childrenByParent))
                .build();
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
        return toResponse(category, Map.of(), Map.of());
    }

    private CategoryResponse toResponse(
            Category category,
            Map<Long, Long> directProductCounts,
            Map<Long, List<Category>> childrenByParent
    ) {
        CategoryResponse response = new CategoryResponse();
        response.setCategoryId(category.getCategoryId());
        response.setBranchId(category.getBranchId());
        response.setName(category.getName());
        response.setParentId(category.getParentId());
        response.setIsActive(category.getIsActive());
        response.setCreatedAt(category.getCreatedAt());
        response.setProductCount(directProductCounts.getOrDefault(category.getCategoryId(), 0L));
        response.setSubcategoryCount((long) childrenByParent.getOrDefault(category.getCategoryId(), List.of()).size());

        // Resolve parent name
        if (category.getParentId() != null) {
            categoryRepository.findById(category.getParentId())
                    .ifPresent(parent -> response.setParentName(parent.getName()));
        }

        return response;
    }

    private Map<Long, Long> buildDirectProductCounts(Long branchId) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        itemRepository.findByBranchId(branchId).stream()
                .map(Item::getCategoryId)
                .filter(categoryId -> categoryId != null)
                .forEach(categoryId -> counts.merge(categoryId, 1L, Long::sum));
        return counts;
    }

    private Map<Long, List<Category>> buildChildrenByParent(List<Category> categories) {
        Map<Long, List<Category>> childrenByParent = new LinkedHashMap<>();
        for (Category category : categories) {
            if (category.getParentId() != null) {
                childrenByParent.computeIfAbsent(category.getParentId(), id -> new ArrayList<>()).add(category);
            }
        }
        childrenByParent.values().forEach(children -> children.sort(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER)));
        return childrenByParent;
    }

    private boolean matchesCategoryTreeFilters(
            Category parent,
            List<Category> children,
            CategorySearchRequestDto filters,
            String keyword
    ) {
        if (filters.getActive() != null) {
            boolean parentMatches = filters.getActive().equals(parent.getIsActive());
            boolean childMatches = children.stream().anyMatch(child -> filters.getActive().equals(child.getIsActive()));
            if (!parentMatches && !childMatches) {
                return false;
            }
        }

        if (StringUtils.hasText(keyword)) {
            boolean parentMatches = matchesCategoryKeyword(parent, keyword);
            boolean childMatches = children.stream().anyMatch(child -> matchesCategoryKeyword(child, keyword));
            return parentMatches || childMatches;
        }

        return true;
    }

    private boolean matchesCategoryKeyword(Category category, String keyword) {
        return safe(category.getName()).toLowerCase().contains(keyword)
                || String.valueOf(category.getCategoryId()).contains(keyword);
    }

    private Comparator<Category> buildCategoryComparator(
            Pageable pageable,
            Map<Long, Long> directProductCounts,
            Map<Long, List<Category>> childrenByParent
    ) {
        var sortOrder = pageable.getSort().stream().findFirst().orElse(null);
        Comparator<Category> comparator;
        String property = sortOrder != null ? sortOrder.getProperty() : "categoryId";

        if ("name".equals(property)) {
            comparator = Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER);
        } else if ("productCount".equals(property)) {
            comparator = Comparator.comparing(category -> getCategoryProductCount(category, directProductCounts, childrenByParent));
        } else if ("createdAt".equals(property)) {
            comparator = Comparator.comparing(Category::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            comparator = Comparator.comparing(Category::getCategoryId, Comparator.nullsLast(Comparator.naturalOrder()));
        }

        if (sortOrder != null && sortOrder.isDescending()) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    private long getCategoryProductCount(
            Category category,
            Map<Long, Long> directProductCounts,
            Map<Long, List<Category>> childrenByParent
    ) {
        long total = directProductCounts.getOrDefault(category.getCategoryId(), 0L);
        for (Category child : childrenByParent.getOrDefault(category.getCategoryId(), List.of())) {
            total += directProductCounts.getOrDefault(child.getCategoryId(), 0L);
        }
        return total;
    }

    private CategorySummaryDto buildCategorySummary(
            List<Category> categories,
            Map<Long, Long> directProductCounts,
            Map<Long, List<Category>> childrenByParent
    ) {
        return CategorySummaryDto.builder()
                .mainCategories(categories.stream().filter(category -> category.getParentId() == null).count())
                .subcategories(categories.stream().filter(category -> category.getParentId() != null).count())
                .emptyCategories(categories.stream().filter(category -> getCategoryProductCount(category, directProductCounts, childrenByParent) == 0).count())
                .build();
    }

    private String formatPageSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return "categoryId,ASC";
        }
        return String.join(";",
                pageable.getSort().stream()
                        .map(order -> order.getProperty() + "," + order.getDirection().name())
                        .toList());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
