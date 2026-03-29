package com.pos.system.service;

import com.pos.system.dto.taxrate.ItemTaxRequest;
import com.pos.system.dto.taxrate.TaxRateRequest;
import com.pos.system.dto.taxrate.TaxRateResponse;
import com.pos.system.model.catalog.ItemTax;
import com.pos.system.model.catalog.TaxRate;
import com.pos.system.repository.BranchRepository;
import com.pos.system.repository.ItemRepository;
import com.pos.system.repository.ItemTaxRepository;
import com.pos.system.repository.TaxRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaxRateService {

    private final TaxRateRepository taxRateRepository;
    private final ItemTaxRepository itemTaxRepository;
    private final BranchRepository branchRepository;
    private final ItemRepository itemRepository;

    public TaxRateResponse create(TaxRateRequest request) {
        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + request.getBranchId()));

        if (taxRateRepository.existsByBranchIdAndName(request.getBranchId(), request.getName())) {
            throw new RuntimeException("Tax rate name already exists in this branch");
        }

        TaxRate taxRate = new TaxRate();
        taxRate.setBranchId(request.getBranchId());
        taxRate.setName(request.getName());
        taxRate.setRatePercent(request.getRatePercent());
        taxRate.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        taxRate.setCreatedAt(LocalDateTime.now());

        return toResponse(taxRateRepository.save(taxRate));
    }

    public List<TaxRateResponse> getAllByBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        return taxRateRepository.findByBranchId(branchId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<TaxRateResponse> getActiveByBranch(Long branchId) {
        branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + branchId));
        return taxRateRepository.findByBranchIdAndIsActive(branchId, true)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public TaxRateResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public TaxRateResponse update(Long id, TaxRateRequest request) {
        TaxRate taxRate = findById(id);

        if (!taxRate.getName().equals(request.getName()) &&
                taxRateRepository.existsByBranchIdAndName(taxRate.getBranchId(), request.getName())) {
            throw new RuntimeException("Tax rate name already exists in this branch");
        }

        taxRate.setName(request.getName());
        taxRate.setRatePercent(request.getRatePercent());
        if (request.getIsActive() != null) {
            taxRate.setIsActive(request.getIsActive());
        }

        return toResponse(taxRateRepository.save(taxRate));
    }

    public TaxRateResponse toggleActive(Long id) {
        TaxRate taxRate = findById(id);
        taxRate.setIsActive(!taxRate.getIsActive());
        return toResponse(taxRateRepository.save(taxRate));
    }

    public void delete(Long id) {
        TaxRate taxRate = findById(id);
        // Check if any items are using this tax rate
        List<ItemTax> itemTaxes = itemTaxRepository.findByBranchId(taxRate.getBranchId())
                .stream().filter(it -> it.getTaxId().equals(id)).collect(Collectors.toList());
        if (!itemTaxes.isEmpty()) {
            throw new RuntimeException("Cannot delete tax rate — it is assigned to " + itemTaxes.size() + " item(s). Remove from items first.");
        }
        taxRateRepository.delete(taxRate);
    }

    // ── Item Tax Assignment ───────────────────────────────────────────────────

    public void assignTaxToItem(Long itemId, ItemTaxRequest request) {
        itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found with id: " + itemId));

        findById(request.getTaxId()); // validate tax exists

        if (itemTaxRepository.existsByItemIdAndTaxId(itemId, request.getTaxId())) {
            throw new RuntimeException("This tax rate is already assigned to this item");
        }

        ItemTax itemTax = new ItemTax();
        itemTax.setItemId(itemId);
        itemTax.setTaxId(request.getTaxId());
        itemTax.setBranchId(itemRepository.findById(itemId).get().getBranchId());
        itemTaxRepository.save(itemTax);
    }

    @Transactional
    public void removeTaxFromItem(Long itemId, Long taxId) {
        if (!itemTaxRepository.existsByItemIdAndTaxId(itemId, taxId)) {
            throw new RuntimeException("This tax rate is not assigned to this item");
        }
        itemTaxRepository.deleteByItemIdAndTaxId(itemId, taxId);
    }

    public List<TaxRateResponse> getTaxesForItem(Long itemId) {
        itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found with id: " + itemId));

        return itemTaxRepository.findByItemId(itemId)
                .stream()
                .map(it -> toResponse(findById(it.getTaxId())))
                .collect(Collectors.toList());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TaxRate findById(Long id) {
        return taxRateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tax rate not found with id: " + id));
    }

    private TaxRateResponse toResponse(TaxRate taxRate) {
        TaxRateResponse response = new TaxRateResponse();
        response.setTaxId(taxRate.getTaxId());
        response.setBranchId(taxRate.getBranchId());
        response.setName(taxRate.getName());
        response.setRatePercent(taxRate.getRatePercent());
        response.setIsActive(taxRate.getIsActive());
        response.setCreatedAt(taxRate.getCreatedAt());
        return response;
    }
}
