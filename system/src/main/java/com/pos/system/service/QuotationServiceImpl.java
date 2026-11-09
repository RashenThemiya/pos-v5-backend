package com.pos.system.service;

import com.pos.system.dto.sale.*;
import com.pos.system.model.sale.Quotation;
import com.pos.system.model.sale.QuotationItem;
import com.pos.system.model.sale.QuotationPromotion;
import com.pos.system.model.promotion.Promotion;
import com.pos.system.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class QuotationServiceImpl implements QuotationService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "SENT", "ACCEPTED", "REJECTED", "EXPIRED", "CANCELLED");
    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final QuotationPromotionRepository quotationPromotionRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final ItemRepository itemRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository promotionRedemptionRepository;

    @Override
    public QuotationResponse create(QuotationRequest request) {
        validateHeader(request);
        Quotation quotation = new Quotation();
        quotation.setQuotationNo(generateNumber(request.getBranchId()));
        quotation.setStatus("DRAFT");
        quotation.setCreatedAt(LocalDateTime.now());
        applyHeader(quotation, request);
        quotationRepository.save(quotation);
        saveItemsAndTotals(quotation, request);
        return toResponse(quotation);
    }

    @Override
    public QuotationResponse update(Long quotationId, QuotationRequest request) {
        validateHeader(request);
        Quotation quotation = find(quotationId);
        if (Set.of("ACCEPTED", "REJECTED", "EXPIRED", "CANCELLED").contains(quotation.getStatus())) {
            throw new IllegalArgumentException("Only DRAFT or SENT quotations can be edited");
        }
        applyHeader(quotation, request);
        quotationItemRepository.deleteByQuotationId(quotationId);
        quotationPromotionRepository.deleteByQuotationId(quotationId);
        saveItemsAndTotals(quotation, request);
        return toResponse(quotation);
    }

    @Override
    public QuotationResponse getById(Long quotationId) { return toResponse(find(quotationId)); }

    @Override
    public QuotationResponse getByNumber(String quotationNo) {
        return toResponse(quotationRepository.findByQuotationNo(quotationNo)
                .orElseThrow(() -> new IllegalArgumentException("Quotation not found: " + quotationNo)));
    }

    @Override
    public Page<QuotationResponse> list(Long branchId, String status, Long customerId, Pageable pageable) {
        if (!branchRepository.existsById(branchId)) throw new IllegalArgumentException("Branch not found: " + branchId);
        String normalizedStatus = status == null || status.isBlank() ? null : normalizeStatus(status);
        Page<Quotation> page;
        if (customerId != null && normalizedStatus != null) page = quotationRepository.findByBranchIdAndCustomerIdAndStatus(branchId, customerId, normalizedStatus, pageable);
        else if (customerId != null) page = quotationRepository.findByBranchIdAndCustomerId(branchId, customerId, pageable);
        else if (normalizedStatus != null) page = quotationRepository.findByBranchIdAndStatus(branchId, normalizedStatus, pageable);
        else page = quotationRepository.findByBranchId(branchId, pageable);
        return page.map(this::toResponse);
    }

    @Override
    public QuotationResponse updateStatus(Long quotationId, String status) {
        Quotation quotation = find(quotationId);
        quotation.setStatus(normalizeStatus(status));
        quotation.setUpdatedAt(LocalDateTime.now());
        quotationRepository.save(quotation);
        return toResponse(quotation);
    }

    private void validateHeader(QuotationRequest request) {
        if (request.getBranchId() == null || !branchRepository.existsById(request.getBranchId())) throw new IllegalArgumentException("Valid branchId is required");
        if (request.getUserId() == null || !userRepository.existsById(request.getUserId())) throw new IllegalArgumentException("Valid userId is required");
        if (request.getCustomerId() != null && !customerRepository.existsById(request.getCustomerId())) throw new IllegalArgumentException("Customer not found: " + request.getCustomerId());
        if (request.getItems() == null || request.getItems().isEmpty()) throw new IllegalArgumentException("Quotation must have at least one item");
        if (nvl(request.getDiscount()).signum() < 0 || nvl(request.getTaxAmount()).signum() < 0) throw new IllegalArgumentException("Discount and taxAmount cannot be negative");
    }

    private void applyHeader(Quotation q, QuotationRequest r) {
        q.setBranchId(r.getBranchId()); q.setUserId(r.getUserId()); q.setCustomerId(r.getCustomerId());
        q.setCustomerName(r.getCustomerName()); q.setCustomerPhone(r.getCustomerPhone()); q.setCustomerEmail(r.getCustomerEmail()); q.setCustomerAddress(r.getCustomerAddress());
        q.setValidUntil(r.getValidUntil()); q.setDiscount(money(nvl(r.getDiscount()))); q.setTaxAmount(money(nvl(r.getTaxAmount())));
        q.setRounding(money(nvl(r.getRounding()))); q.setNotes(r.getNotes()); q.setTerms(r.getTerms()); q.setUpdatedAt(LocalDateTime.now());
    }

    private void saveItemsAndTotals(Quotation q, QuotationRequest r) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal itemDiscount = BigDecimal.ZERO;
        for (QuotationItemRequest line : r.getItems()) {
            validateLine(q.getBranchId(), line);
            BigDecimal gross = line.getUnitPrice().multiply(line.getQuantity());
            BigDecimal lineDiscount = nvl(line.getDiscount());
            if (lineDiscount.signum() < 0 || lineDiscount.compareTo(gross) > 0) throw new IllegalArgumentException("Item discount must be between zero and line subtotal");
            QuotationItem item = new QuotationItem();
            item.setQuotationId(q.getQuotationId()); item.setItemId(line.getItemId()); item.setVariantId(line.getVariantId()); item.setUnitId(line.getUnitId()); item.setBatchBarcode(line.getBatchBarcode());
            item.setQuantity(line.getQuantity()); item.setUnitPrice(money(line.getUnitPrice())); item.setDiscount(money(lineDiscount)); item.setLineTotal(money(gross.subtract(lineDiscount)));
            if (line.getPromotionId() != null && lineDiscount.signum() > 0) {
                Promotion promotion = requirePromotion(q, line.getPromotionId());
                if (Set.of("PERCENTAGE", "FIXED").contains(promotion.getType())) throw new IllegalArgumentException("Bill promotions must be applied at bill level");
                item.setPromotionId(promotion.getPromotionId()); item.setPromotionName(promotion.getName()); item.setPromotionType(promotion.getType()); item.setPromotionValue(promotion.getValue());
            }
            quotationItemRepository.save(item);
            subtotal = subtotal.add(gross); itemDiscount = itemDiscount.add(lineDiscount);
        }
        subtotal = money(subtotal); itemDiscount = money(itemDiscount);
        BigDecimal netBeforeBillDiscount = subtotal.subtract(itemDiscount);
        if (q.getDiscount().compareTo(netBeforeBillDiscount) > 0) throw new IllegalArgumentException("Bill discount cannot exceed subtotal after item discounts");
        saveBillPromotions(q, r.getBillPromotionIds(), netBeforeBillDiscount);
        q.setSubtotal(subtotal); q.setItemDiscount(itemDiscount);
        q.setTotal(money(netBeforeBillDiscount.subtract(q.getDiscount()).add(q.getTaxAmount()).add(q.getRounding())).max(BigDecimal.ZERO));
        quotationRepository.save(q);
    }

    private void saveBillPromotions(Quotation quotation, List<Long> promotionIds, BigDecimal subtotal) {
        if (promotionIds == null || promotionIds.isEmpty()) {
            if (quotation.getDiscount().signum() > 0) return; // manual bill discount, same as sales
            return;
        }
        List<Promotion> selected = new java.util.LinkedHashSet<>(promotionIds).stream().map(id -> requirePromotion(quotation, id)).toList();
        if (selected.size() > 1 && selected.stream().anyMatch(p -> !Boolean.TRUE.equals(p.getIsStackable()))) throw new IllegalArgumentException("Selected bill promotions cannot be combined");
        BigDecimal runningTotal = subtotal;
        BigDecimal attributed = BigDecimal.ZERO;
        for (Promotion promotion : selected) {
            if (!Set.of("PERCENTAGE", "FIXED").contains(promotion.getType())) throw new IllegalArgumentException("Only bill promotions may be applied at bill level");
            if (promotion.getMinBillTotal() != null && runningTotal.compareTo(promotion.getMinBillTotal()) < 0) throw new IllegalArgumentException("Bill does not meet the promotion minimum");
            BigDecimal calculated = "PERCENTAGE".equals(promotion.getType())
                    ? runningTotal.multiply(promotion.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP) : promotion.getValue();
            BigDecimal amount = money(calculated.max(BigDecimal.ZERO).min(runningTotal)).min(quotation.getDiscount().subtract(attributed).max(BigDecimal.ZERO));
            if (amount.signum() == 0) continue;
            QuotationPromotion snapshot = new QuotationPromotion();
            snapshot.setQuotationId(quotation.getQuotationId()); snapshot.setPromotionId(promotion.getPromotionId()); snapshot.setPromotionName(promotion.getName());
            snapshot.setPromoCode(promotion.getPromoCode()); snapshot.setPromotionType(promotion.getType()); snapshot.setPromotionValue(promotion.getValue()); snapshot.setDiscountAmount(amount);
            quotationPromotionRepository.save(snapshot);
            attributed = attributed.add(amount); runningTotal = runningTotal.subtract(amount);
        }
    }

    private Promotion requirePromotion(Quotation quotation, Long promotionId) {
        Promotion promotion = promotionRepository.findById(promotionId).orElseThrow(() -> new IllegalArgumentException("Promotion not found: " + promotionId));
        LocalDateTime now = quotation.getCreatedAt();
        if (!quotation.getBranchId().equals(promotion.getBranchId()) || !Boolean.TRUE.equals(promotion.getIsActive())
                || (promotion.getStartAt() != null && now.isBefore(promotion.getStartAt())) || (promotion.getEndAt() != null && now.isAfter(promotion.getEndAt()))) {
            throw new IllegalArgumentException("Promotion is not available for this quotation: " + promotionId);
        }
        // Only completed-sale redemptions count. Saving a quotation never consumes a promotion use.
        if (promotion.getMaxUsesTotal() != null && promotionRedemptionRepository.countByPromotionId(promotionId) >= promotion.getMaxUsesTotal()) throw new IllegalArgumentException("Promotion usage limit reached");
        if (quotation.getCustomerId() != null && promotion.getMaxUsesPerCustomer() != null
                && promotionRedemptionRepository.countByPromotionIdAndCustomerId(promotionId, quotation.getCustomerId()) >= promotion.getMaxUsesPerCustomer()) throw new IllegalArgumentException("Customer promotion usage limit reached");
        return promotion;
    }

    private void validateLine(Long branchId, QuotationItemRequest line) {
        if (line.getItemId() == null || itemRepository.findByItemIdAndBranchId(line.getItemId(), branchId).isEmpty()) throw new IllegalArgumentException("Item does not belong to branch: " + line.getItemId());
        if (line.getVariantId() != null && itemVariantRepository.findByVariantIdAndItemId(line.getVariantId(), line.getItemId()).isEmpty()) throw new IllegalArgumentException("Variant does not belong to item: " + line.getVariantId());
        if (line.getUnitId() != null && itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(line.getItemId(), line.getUnitId()).isEmpty()) throw new IllegalArgumentException("Unit does not belong to item or is inactive: " + line.getUnitId());
        if (line.getQuantity() == null || line.getQuantity().signum() <= 0) throw new IllegalArgumentException("Item quantity must be greater than zero");
        if (line.getUnitPrice() == null || line.getUnitPrice().signum() < 0) throw new IllegalArgumentException("Item unitPrice cannot be negative");
    }

    private Quotation find(Long id) { return quotationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Quotation not found: " + id)); }
    private String normalizeStatus(String status) {
        if (status == null) throw new IllegalArgumentException("status is required");
        String value = status.trim().toUpperCase();
        if (!STATUSES.contains(value)) throw new IllegalArgumentException("Invalid status. Allowed: " + STATUSES);
        return value;
    }
    private String generateNumber(Long branchId) {
        String prefix = "QT-B" + branchId + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        String number;
        do { number = prefix + UUID.randomUUID().toString().substring(0, 6).toUpperCase(); } while (quotationRepository.existsByQuotationNo(number));
        return number;
    }
    private QuotationResponse toResponse(Quotation q) {
        List<QuotationItemResponse> items = quotationItemRepository.findByQuotationIdOrderByQuotationItemId(q.getQuotationId()).stream().map(i -> QuotationItemResponse.builder()
                .quotationItemId(i.getQuotationItemId()).itemId(i.getItemId()).variantId(i.getVariantId()).unitId(i.getUnitId()).batchBarcode(i.getBatchBarcode())
                .promotionId(i.getPromotionId()).promotionName(i.getPromotionName()).promotionType(i.getPromotionType()).promotionValue(i.getPromotionValue())
                .quantity(i.getQuantity()).unitPrice(i.getUnitPrice()).discount(i.getDiscount()).lineTotal(i.getLineTotal()).build()).toList();
        List<AppliedPromotionResponse> promotions = quotationPromotionRepository.findByQuotationIdOrderByQuotationPromotionId(q.getQuotationId()).stream()
                .map(p -> AppliedPromotionResponse.builder().promotionId(p.getPromotionId()).promotionName(p.getPromotionName()).promoCode(p.getPromoCode())
                        .promotionType(p.getPromotionType()).discountAmount(p.getDiscountAmount()).build()).toList();
        return QuotationResponse.builder().quotationId(q.getQuotationId()).quotationNo(q.getQuotationNo()).branchId(q.getBranchId()).userId(q.getUserId()).customerId(q.getCustomerId())
                .customerName(q.getCustomerName()).customerPhone(q.getCustomerPhone()).customerEmail(q.getCustomerEmail()).customerAddress(q.getCustomerAddress()).status(q.getStatus()).validUntil(q.getValidUntil())
                .subtotal(q.getSubtotal()).itemDiscount(q.getItemDiscount()).discount(q.getDiscount()).totalDiscount(q.getItemDiscount().add(q.getDiscount())).taxAmount(q.getTaxAmount()).rounding(q.getRounding()).total(q.getTotal())
                .notes(q.getNotes()).terms(q.getTerms()).createdAt(q.getCreatedAt()).updatedAt(q.getUpdatedAt()).items(items).appliedPromotions(promotions).build();
    }
    private BigDecimal nvl(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
