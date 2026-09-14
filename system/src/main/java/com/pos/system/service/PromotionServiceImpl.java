package com.pos.system.service;

import com.pos.system.dto.promotion.*;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.ItemVariant;
import com.pos.system.model.catalog.UnitMaster;
import com.pos.system.model.promotion.*;
import com.pos.system.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionItemRepository promotionItemRepository;
    private final PromotionBatchRepository promotionBatchRepository;
    private final PromotionBuyXGetYRuleRepository buyXGetYRuleRepository;
    private final PromotionRedemptionRepository redemptionRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final ItemVariantAttributeRepository itemVariantAttributeRepository;
    private final UnitMasterRepository unitMasterRepository;

    // ─── Promotion CRUD ──────────────────────────────────────────────────────────

    @Override
    public PromotionResponse createPromotion(PromotionRequest request) {
        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            promotionRepository.findByBranchIdAndPromoCode(request.getBranchId(), request.getPromoCode())
                    .ifPresent(x -> {
                        throw new RuntimeException("Promo code already exists in this branch: " + request.getPromoCode());
                    });
        }
        Promotion p = new Promotion();
        mapRequestToPromotion(request, p);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return mapPromotion(promotionRepository.save(p), false);
    }

    @Override
    public PromotionResponse updatePromotion(Long promotionId, PromotionRequest request) {
        Promotion p = findPromoById(promotionId);
        if (request.getPromoCode() != null && !request.getPromoCode().equals(p.getPromoCode())) {
            promotionRepository.findByBranchIdAndPromoCode(p.getBranchId(), request.getPromoCode())
                    .ifPresent(x -> {
                        throw new RuntimeException("Promo code already exists: " + request.getPromoCode());
                    });
        }
        mapRequestToPromotion(request, p);
        p.setUpdatedAt(LocalDateTime.now());
        return mapPromotion(promotionRepository.save(p), false);
    }

    @Override
    public PromotionResponse getPromotionById(Long promotionId) {
        return mapActivePromotions(findPromoById(promotionId), true);
    }

    @Override
    public List<PromotionResponse> getPromotionsByBranch(Long branchId) {
        return promotionRepository.findByBranchId(branchId)
                .stream().map(p -> mapPromotion(p, false)).toList();
    }

    @Override
    public List<PromotionResponse> getPromotionsByItem(Long branchId, Long itemId) {
        return promotionRepository.findByBranchIdAndItemId(branchId, itemId)
                .stream().map(p -> mapPromotion(p, false)).toList();
    }

    @Override
    public List<PromotionResponse> getActivePromotionsByBranch(Long branchId) {
        return promotionRepository.findActiveByBranchIdAndNow(branchId, LocalDateTime.now())
                .stream().map(p -> mapPromotion(p, false)).toList();
    }

    @Override
    public void togglePromotion(Long promotionId, boolean isActive) {
        Promotion p = findPromoById(promotionId);
        p.setIsActive(isActive);
        p.setUpdatedAt(LocalDateTime.now());
        promotionRepository.save(p);
    }

    // ─── Items ───────────────────────────────────────────────────────────────────

    @Override
    public PromotionItemResponse addItemToPromotion(Long promotionId, PromotionItemRequest request) {
        Promotion promo = findPromoById(promotionId);

        if (!promo.getType().equals("ITEM_PERCENTAGE") && !promo.getType().equals("ITEM_FIXED")) {
            throw new RuntimeException("Items can only be added to ITEM_PERCENTAGE or ITEM_FIXED promotions");
        }

        boolean alreadyLinked = promotionItemRepository.findByPromotionId(promotionId).stream()
                .anyMatch(existing -> Boolean.TRUE.equals(existing.getIsActive())
                        && Objects.equals(existing.getItemId(), request.getItemId())
                        && Objects.equals(existing.getVariantId(), request.getVariantId())
                        && Objects.equals(existing.getUnitId(), request.getUnitId()));
        if (alreadyLinked) {
            throw new RuntimeException("Item, variant, and unit are already linked to this promotion");
        }

        // validate unit belongs to item
        validateUnitBelongsToItem(request.getItemId(), request.getUnitId());
        validateVariantBelongsToItem(request.getBranchId(), request.getItemId(), request.getVariantId());

        PromotionItem item = new PromotionItem();
        item.setBranchId(request.getBranchId());
        item.setPromotionId(promotionId);
        item.setItemId(request.getItemId());
        item.setVariantId(request.getVariantId());
        item.setUnitId(request.getUnitId());
        item.setMaxQty(request.getMaxQty());
        item.setUsedQty(BigDecimal.ZERO);
        item.setIsActive(true);

        return mapPromotionItem(promotionItemRepository.save(item));
    }

    @Override
    public void removeItemFromPromotion(Long promotionId, Long itemId) {
        PromotionItem item = promotionItemRepository.findById(itemId)
                .filter(link -> Objects.equals(link.getPromotionId(), promotionId))
                .or(() -> promotionItemRepository.findByPromotionIdAndItemId(promotionId, itemId))
                .orElseThrow(() -> new RuntimeException("Item not found in promotion"));
        item.setIsActive(false);
        promotionItemRepository.save(item);
    }

    @Override
    public List<PromotionItemResponse> getItemsByPromotion(Long promotionId) {
        return promotionItemRepository.findByPromotionId(promotionId)
                .stream().map(this::mapPromotionItem).toList();
    }

    // ─── Batches ─────────────────────────────────────────────────────────────────

    @Override
    public PromotionBatchResponse addBatchToPromotion(Long promotionId, PromotionBatchRequest request) {
        Promotion promo = findPromoById(promotionId);

        if (!promo.getType().equals("BATCH")) {
            throw new RuntimeException("Batches can only be added to BATCH type promotions");
        }

        promotionBatchRepository.findByPromotionIdAndBarcode(promotionId, request.getBarcode())
                .ifPresent(x -> {
                    throw new RuntimeException("Batch barcode already linked to this promotion");
                });

        PromotionBatch batch = new PromotionBatch();
        batch.setBranchId(request.getBranchId());
        batch.setPromotionId(promotionId);
        batch.setBarcode(request.getBarcode());
        batch.setMaxQty(request.getMaxQty());
        batch.setUsedQty(BigDecimal.ZERO);
        batch.setIsActive(true);

        return mapPromotionBatch(promotionBatchRepository.save(batch));
    }

    @Override
    public void removeBatchFromPromotion(Long promotionId, Long batchId) {
        PromotionBatch batch = promotionBatchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));
        batch.setIsActive(false);
        promotionBatchRepository.save(batch);
    }

    @Override
    public List<PromotionBatchResponse> getBatchesByPromotion(Long promotionId) {
        return promotionBatchRepository.findByPromotionId(promotionId)
                .stream().map(this::mapPromotionBatch).toList();
    }

    // ─── BuyXGetY Rules ──────────────────────────────────────────────────────────

    @Override
    public BuyXGetYRuleResponse addBuyXGetYRule(Long promotionId, BuyXGetYRuleRequest request) {
        Promotion promo = findPromoById(promotionId);

        if (!promo.getType().equals("BUY_X_GET_Y")) {
            throw new RuntimeException("Rules can only be added to BUY_X_GET_Y type promotions");
        }

        validateUnitBelongsToItem(request.getBuyItemId(), request.getBuyUnitId());
        validateUnitBelongsToItem(request.getGetItemId(), request.getGetUnitId());
        validateVariantBelongsToItem(request.getBranchId(), request.getBuyItemId(), request.getBuyVariantId());
        validateVariantBelongsToItem(request.getBranchId(), request.getGetItemId(), request.getGetVariantId());

        PromotionBuyXGetYRule rule = new PromotionBuyXGetYRule();
        rule.setBranchId(request.getBranchId());
        rule.setPromotionId(promotionId);
        rule.setBuyItemId(request.getBuyItemId());
        rule.setBuyVariantId(request.getBuyVariantId());
        rule.setBuyUnitId(request.getBuyUnitId());
        rule.setBuyQty(request.getBuyQty());
        rule.setGetItemId(request.getGetItemId());
        rule.setGetVariantId(request.getGetVariantId());
        rule.setGetUnitId(request.getGetUnitId());
        rule.setGetQty(request.getGetQty());
        rule.setGetDiscountPercent(
                request.getGetDiscountPercent() != null ? request.getGetDiscountPercent() : BigDecimal.valueOf(100)
        );

        return mapRule(buyXGetYRuleRepository.save(rule));
    }

    @Override
    public void removeBuyXGetYRule(Long ruleId) {
        PromotionBuyXGetYRule promotionBuyXGetYRule = buyXGetYRuleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + ruleId));
        promotionBuyXGetYRule.setIsActive(false);

        buyXGetYRuleRepository.save(promotionBuyXGetYRule);
    }

    @Override
    public List<BuyXGetYRuleResponse> getRulesByPromotion(Long promotionId) {
        return buyXGetYRuleRepository.findByPromotionId(promotionId)
                .stream().map(this::mapRule).toList();
    }

    // ─── Apply ───────────────────────────────────────────────────────────────────

    @Override
    public ApplyPromotionResponse applyPromotion(Long promotionId, ApplyPromotionRequest request) {
        Promotion promo = findPromoById(promotionId);
        validatePromotion(promo, request);
        return calculateDiscount(promo, request);
    }

    @Override
    public ApplyPromotionResponse applyPromotionByCode(ApplyPromotionRequest request) {
        if (request.getPromoCode() == null || request.getPromoCode().isBlank()) {
            throw new RuntimeException("Promo code is required");
        }
        Promotion promo = promotionRepository.findByBranchIdAndPromoCode(request.getBranchId(), request.getPromoCode())
                .orElseThrow(() -> new RuntimeException("Invalid promo code: " + request.getPromoCode()));
        validatePromotion(promo, request);
        return calculateDiscount(promo, request);
    }

    @Override
    public List<ApplyPromotionResponse> getApplicablePromotions(ApplyPromotionRequest request) {
        List<Promotion> active = promotionRepository.findActiveByBranchIdAndNow(request.getBranchId(), LocalDateTime.now());
        List<ApplyPromotionResponse> results = new ArrayList<>();

        for (Promotion promo : active) {
            try {
                validatePromotion(promo, request);
                ApplyPromotionResponse result = calculateDiscount(promo, request);
                if (result.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                    results.add(result);
                }
            } catch (Exception ignored) {
                // not applicable - skip
            }
        }

        results.sort((a, b) -> b.getDiscountAmount().compareTo(a.getDiscountAmount()));
        return results;
    }

    // ─── Core discount calculation ────────────────────────────────────────────────

    private void validatePromotion(Promotion promo, ApplyPromotionRequest request) {
        if (!Boolean.TRUE.equals(promo.getIsActive())) {
            throw new RuntimeException("Promotion is not active");
        }
        LocalDateTime now = LocalDateTime.now();
        if (promo.getStartAt() != null && now.isBefore(promo.getStartAt())) {
            throw new RuntimeException("Promotion has not started yet");
        }
        if (promo.getEndAt() != null && now.isAfter(promo.getEndAt())) {
            throw new RuntimeException("Promotion has expired");
        }
        if (promo.getMinBillTotal() != null &&
                request.getBillTotal().compareTo(promo.getMinBillTotal()) < 0) {
            throw new RuntimeException("Bill total does not meet minimum of " + promo.getMinBillTotal());
        }
        if (promo.getMaxUsesTotal() != null) {
            long used = redemptionRepository.countByPromotionId(promo.getPromotionId());
            if (used >= promo.getMaxUsesTotal()) {
                throw new RuntimeException("Promotion has reached its maximum usage limit");
            }
        }
        if (promo.getMaxUsesPerCustomer() != null && request.getCustomerId() != null) {
            long usedByCustomer = redemptionRepository.countByPromotionIdAndCustomerId(
                    promo.getPromotionId(), request.getCustomerId());
            if (usedByCustomer >= promo.getMaxUsesPerCustomer()) {
                throw new RuntimeException("Customer has reached max uses for this promotion");
            }
        }
    }

    private ApplyPromotionResponse calculateDiscount(Promotion promo, ApplyPromotionRequest request) {
        BigDecimal discountAmount = BigDecimal.ZERO;
        List<ApplyPromotionResponse.LineDiscountDto> lineDiscounts = new ArrayList<>();

        switch (promo.getType()) {

            case "PERCENTAGE" -> {
                discountAmount = request.getBillTotal()
                        .multiply(promo.getValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }

            case "FIXED" -> {
                discountAmount = promo.getValue().min(request.getBillTotal());
            }

            case "ITEM_PERCENTAGE" -> {
                List<PromotionItem> promoItems = promotionItemRepository.findByPromotionIdAndIsActiveTrue(promo.getPromotionId());
                for (ApplyPromotionRequest.CartItemDto cartItem : request.getCartItems()) {
                    promoItems.stream()
                            .filter(pi -> pi.getItemId().equals(cartItem.getItemId())
                                    && variantApplies(pi.getVariantId(), cartItem.getVariantId()))
                            .findFirst()
                            .ifPresent(pi -> {
                                // convert both qtys to base units to compare correctly
                                BigDecimal cartQtyInBase = toBaseQty(cartItem.getItemId(), cartItem.getUnitId(), cartItem.getQty());
                                BigDecimal promoQtyInBase = toBaseQty(pi.getItemId(), pi.getUnitId(), pi.getMaxQty() != null ? pi.getMaxQty() : cartItem.getQty());

                                // only discount up to maxQty if set
                                BigDecimal applicableQtyInBase = pi.getMaxQty() != null
                                        ? cartQtyInBase.min(promoQtyInBase)
                                        : cartQtyInBase;

                                // convert back to cart unit price basis
                                BigDecimal cartUnitMultiplier = getMultiplierToBase(cartItem.getItemId(), cartItem.getUnitId());
                                BigDecimal applicableQtyInCartUnit = cartUnitMultiplier.compareTo(BigDecimal.ZERO) == 0
                                        ? BigDecimal.ZERO
                                        : applicableQtyInBase.divide(cartUnitMultiplier, 4, RoundingMode.HALF_UP);

                                BigDecimal lineDiscount = cartItem.getUnitPrice()
                                        .multiply(applicableQtyInCartUnit)
                                        .multiply(promo.getValue())
                                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                                lineDiscounts.add(ApplyPromotionResponse.LineDiscountDto.builder()
                                        .itemId(cartItem.getItemId())
                                        .discountAmount(lineDiscount)
                                        .reason(promo.getValue() + "% off (unit: " + getUnitName(pi.getUnitId()) + variantReason(pi.getVariantId()) + ")")
                                        .build());
                            });
                }
                discountAmount = lineDiscounts.stream()
                        .map(ApplyPromotionResponse.LineDiscountDto::getDiscountAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            case "ITEM_FIXED" -> {
                List<PromotionItem> promoItems = promotionItemRepository.findByPromotionIdAndIsActiveTrue(promo.getPromotionId());
                for (ApplyPromotionRequest.CartItemDto cartItem : request.getCartItems()) {
                    promoItems.stream()
                            .filter(pi -> pi.getItemId().equals(cartItem.getItemId())
                                    && variantApplies(pi.getVariantId(), cartItem.getVariantId()))
                            .findFirst()
                            .ifPresent(pi -> {
                                BigDecimal lineTotal = cartItem.getUnitPrice().multiply(cartItem.getQty());
                                BigDecimal lineDiscount = promo.getValue().min(lineTotal);
                                lineDiscounts.add(ApplyPromotionResponse.LineDiscountDto.builder()
                                        .itemId(cartItem.getItemId())
                                        .discountAmount(lineDiscount)
                                        .reason("Fixed " + promo.getValue() + " off (unit: " + getUnitName(pi.getUnitId()) + variantReason(pi.getVariantId()) + ")")
                                        .build());
                            });
                }
                discountAmount = lineDiscounts.stream()
                        .map(ApplyPromotionResponse.LineDiscountDto::getDiscountAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            case "BUY_X_GET_Y" -> {
                List<PromotionBuyXGetYRule> rules = buyXGetYRuleRepository.findByPromotionId(promo.getPromotionId());
                for (PromotionBuyXGetYRule rule : rules) {

                    // required buy qty in base units
                    BigDecimal requiredBuyQtyInBase = toBaseQty(rule.getBuyItemId(), rule.getBuyUnitId(), rule.getBuyQty());

                    // check cart has enough of the buy item (in base units)
                    BigDecimal cartBuyQtyInBase = request.getCartItems().stream()
                            .filter(c -> c.getItemId().equals(rule.getBuyItemId())
                                    && variantApplies(rule.getBuyVariantId(), c.getVariantId()))
                            .map(c -> toBaseQty(c.getItemId(), c.getUnitId(), c.getQty()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    if (cartBuyQtyInBase.compareTo(requiredBuyQtyInBase) >= 0) {

                        // required get qty in base units
                        BigDecimal requiredGetQtyInBase = toBaseQty(rule.getGetItemId(), rule.getGetUnitId(), rule.getGetQty());

                        request.getCartItems().stream()
                                .filter(c -> c.getItemId().equals(rule.getGetItemId())
                                        && variantApplies(rule.getGetVariantId(), c.getVariantId()))
                                .findFirst()
                                .ifPresent(getItem -> {
                                    BigDecimal cartGetQtyInBase = toBaseQty(getItem.getItemId(), getItem.getUnitId(), getItem.getQty());
                                    BigDecimal discountableQtyInBase = cartGetQtyInBase.min(requiredGetQtyInBase);

                                    // convert discountable base qty back to cart unit for price calc
                                    BigDecimal cartGetMultiplier = getMultiplierToBase(getItem.getItemId(), getItem.getUnitId());
                                    BigDecimal discountableQtyInCartUnit = cartGetMultiplier.compareTo(BigDecimal.ZERO) == 0
                                            ? BigDecimal.ZERO
                                            : discountableQtyInBase.divide(cartGetMultiplier, 4, RoundingMode.HALF_UP);

                                    BigDecimal lineDiscount = getItem.getUnitPrice()
                                            .multiply(discountableQtyInCartUnit)
                                            .multiply(rule.getGetDiscountPercent())
                                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                                    lineDiscounts.add(ApplyPromotionResponse.LineDiscountDto.builder()
                                            .itemId(rule.getGetItemId())
                                            .discountAmount(lineDiscount)
                                            .reason("Buy " + rule.getBuyQty() + " " + getUnitName(rule.getBuyUnitId())
                                                    + " Get " + rule.getGetQty() + " " + getUnitName(rule.getGetUnitId())
                                                    + " (" + rule.getGetDiscountPercent() + "% off)")
                                            .build());
                                });
                    }
                }
                discountAmount = lineDiscounts.stream()
                        .map(ApplyPromotionResponse.LineDiscountDto::getDiscountAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            case "BATCH" -> {
                for (ApplyPromotionRequest.CartItemDto cartItem : request.getCartItems()) {
                    if (cartItem.getBatchBarcode() == null) continue;
                    promotionBatchRepository
                            .findByBranchIdAndBarcodeAndIsActiveTrue(promo.getBranchId(), cartItem.getBatchBarcode())
                            .ifPresent(pb -> {
                                BigDecimal lineTotal = cartItem.getUnitPrice().multiply(cartItem.getQty());
                                BigDecimal lineDiscount = lineTotal
                                        .multiply(promo.getValue())
                                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                                lineDiscounts.add(ApplyPromotionResponse.LineDiscountDto.builder()
                                        .itemId(cartItem.getItemId())
                                        .discountAmount(lineDiscount)
                                        .reason("Batch promo: " + promo.getValue() + "% off")
                                        .build());
                            });
                }
                discountAmount = lineDiscounts.stream()
                        .map(ApplyPromotionResponse.LineDiscountDto::getDiscountAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            default -> throw new RuntimeException("Unknown promotion type: " + promo.getType());
        }

        BigDecimal finalTotal = request.getBillTotal().subtract(discountAmount).max(BigDecimal.ZERO);

        return ApplyPromotionResponse.builder()
                .promotionId(promo.getPromotionId())
                .promotionName(promo.getName())
                .promoType(promo.getType())
                .discountAmount(discountAmount)
                .finalBillTotal(finalTotal)
                .lineDiscounts(lineDiscounts)
                .build();
    }

    // ─── Unit conversion helpers ──────────────────────────────────────────────────

    /**
     * Converts a qty in the given unit to base unit qty.
     * e.g. qty=2, unit=Box (multiplierToBase=12) → 24 base units
     */
    private BigDecimal toBaseQty(Long itemId, Long unitId, BigDecimal qty) {
        BigDecimal multiplier = getMultiplierToBase(itemId, unitId);
        return qty.multiply(multiplier);
    }

    private BigDecimal getMultiplierToBase(Long itemId, Long unitId) {
        return itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(itemId, unitId)
                .map(ItemUnit::getMultiplierToBase)
                .orElseThrow(() -> new RuntimeException(
                        "Unit " + unitId + " not found for item " + itemId));
    }

    private String getUnitName(Long unitId) {
        return itemUnitRepository.findById(unitId)
                .map(this::resolveUnitName)
                .orElse("unit");
    }

    private Long getMasterUnitId(Long unitId) {
        return itemUnitRepository.findById(unitId)
                .map(ItemUnit::getMasterUnitId)
                .orElse(null);
    }

    private void validateUnitBelongsToItem(Long itemId, Long unitId) {
        itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(itemId, unitId)
                .orElseThrow(() -> new RuntimeException(
                        "Unit " + unitId + " does not belong to item " + itemId));
    }

    private void validateVariantBelongsToItem(Long branchId, Long itemId, Long variantId) {
        if (variantId == null) {
            return;
        }

        ItemVariant variant = itemVariantRepository.findByVariantIdAndItemId(variantId, itemId)
                .orElseThrow(() -> new RuntimeException("Variant not found for item: " + variantId));
        if (!Objects.equals(variant.getBranchId(), branchId)) {
            throw new RuntimeException("Variant does not belong to branch: " + variantId);
        }
        if (!Boolean.TRUE.equals(variant.getIsActive())) {
            throw new RuntimeException("Variant is inactive: " + variantId);
        }
    }

    private boolean variantApplies(Long promotionVariantId, Long cartVariantId) {
        return promotionVariantId == null || Objects.equals(promotionVariantId, cartVariantId);
    }

    private String variantReason(Long variantId) {
        String label = resolveVariantLabel(variantId);
        return label == null ? "" : ", variant: " + label;
    }

    private String resolveVariantSku(Long variantId) {
        if (variantId == null) {
            return null;
        }
        return itemVariantRepository.findById(variantId)
                .map(ItemVariant::getVariantSku)
                .orElse(null);
    }

    private String resolveVariantLabel(Long variantId) {
        if (variantId == null) {
            return null;
        }

        String label = itemVariantAttributeRepository.findByVariantIdOrderByAttributeNameAsc(variantId)
                .stream()
                .map(attribute -> attribute.getAttributeName() + ": " + attribute.getAttributeValue())
                .collect(Collectors.joining(" / "));

        if (label != null && !label.isBlank()) {
            return label;
        }

        return resolveVariantSku(variantId);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────────

    private void mapRequestToPromotion(PromotionRequest request, Promotion p) {
        p.setBranchId(request.getBranchId());
        p.setName(request.getName());
        p.setPromoCode(request.getPromoCode());
        p.setType(request.getType());
        p.setValue(request.getValue());
        p.setMinBillTotal(request.getMinBillTotal());
        p.setStartAt(request.getStartAt());
        p.setEndAt(request.getEndAt());
        p.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        p.setMaxUsesTotal(request.getMaxUsesTotal());
        p.setMaxUsesPerCustomer(request.getMaxUsesPerCustomer());
        p.setIsStackable(request.getIsStackable() != null ? request.getIsStackable() : false);
        p.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        p.setCreatedBy(request.getCreatedBy());
    }

    private Promotion findPromoById(Long promotionId) {
        return promotionRepository.findById(promotionId)
                .orElseThrow(() -> new RuntimeException("Promotion not found: " + promotionId));
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────────

    private PromotionResponse mapPromotion(Promotion p, boolean includeDetails) {
        PromotionResponse.PromotionResponseBuilder builder = PromotionResponse.builder()
                .promotionId(p.getPromotionId())
                .branchId(p.getBranchId())
                .name(p.getName())
                .promoCode(p.getPromoCode())
                .type(p.getType())
                .value(p.getValue())
                .minBillTotal(p.getMinBillTotal())
                .startAt(p.getStartAt())
                .endAt(p.getEndAt())
                .priority(p.getPriority())
                .maxUsesTotal(p.getMaxUsesTotal())
                .maxUsesPerCustomer(p.getMaxUsesPerCustomer())
                .isStackable(p.getIsStackable())
                .isActive(p.getIsActive())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .totalRedemptions(redemptionRepository.countByPromotionId(p.getPromotionId()));

        if (includeDetails) {
            builder.items(promotionItemRepository.findByPromotionId(p.getPromotionId())
                    .stream().map(this::mapPromotionItem).toList());
            builder.batches(promotionBatchRepository.findByPromotionId(p.getPromotionId())
                    .stream().map(this::mapPromotionBatch).toList());
            builder.buyXGetYRules(buyXGetYRuleRepository.findByPromotionId(p.getPromotionId())
                    .stream().map(this::mapRule).toList());
        }

        return builder.build();
    }

    private PromotionItemResponse mapPromotionItem(PromotionItem i) {
        return PromotionItemResponse.builder()
                .id(i.getId())
                .promotionId(i.getPromotionId())
                .itemId(i.getItemId())
                .variantId(i.getVariantId())
                .variantSku(resolveVariantSku(i.getVariantId()))
                .variantLabel(resolveVariantLabel(i.getVariantId()))
                .unitId(i.getUnitId())
                .masterUnitId(getMasterUnitId(i.getUnitId()))
                .unitName(getUnitName(i.getUnitId()))
                .maxQty(i.getMaxQty())
                .usedQty(i.getUsedQty())
                .isActive(i.getIsActive())
                .build();
    }

    private PromotionBatchResponse mapPromotionBatch(PromotionBatch b) {
        return PromotionBatchResponse.builder()
                .id(b.getId())
                .promotionId(b.getPromotionId())
                .barcode(b.getBarcode())
                .maxQty(b.getMaxQty())
                .usedQty(b.getUsedQty())
                .isActive(b.getIsActive())
                .build();
    }

    private BuyXGetYRuleResponse mapRule(PromotionBuyXGetYRule r) {
        return BuyXGetYRuleResponse.builder()
                .ruleId(r.getRuleId())
                .promotionId(r.getPromotionId())
                .buyItemId(r.getBuyItemId())
                .buyVariantId(r.getBuyVariantId())
                .buyVariantSku(resolveVariantSku(r.getBuyVariantId()))
                .buyVariantLabel(resolveVariantLabel(r.getBuyVariantId()))
                .buyUnitId(r.getBuyUnitId())
                .buyMasterUnitId(getMasterUnitId(r.getBuyUnitId()))
                .buyUnitName(getUnitName(r.getBuyUnitId()))
                .buyQty(r.getBuyQty())
                .getItemId(r.getGetItemId())
                .getVariantId(r.getGetVariantId())
                .getVariantSku(resolveVariantSku(r.getGetVariantId()))
                .getVariantLabel(resolveVariantLabel(r.getGetVariantId()))
                .getUnitId(r.getGetUnitId())
                .getMasterUnitId(getMasterUnitId(r.getGetUnitId()))
                .getUnitName(getUnitName(r.getGetUnitId()))
                .getQty(r.getGetQty())
                .getDiscountPercent(r.getGetDiscountPercent())
                .build();
    }

    private String resolveUnitName(ItemUnit unit) {
        if (unit.getMasterUnitId() != null) {
            return unitMasterRepository.findById(unit.getMasterUnitId())
                    .map(UnitMaster::getName)
                    .orElse(unit.getUnitName());
        }

        return unit.getUnitName();
    }

    private PromotionResponse mapActivePromotions(Promotion p, boolean includeDetails) {
        PromotionResponse.PromotionResponseBuilder builder = PromotionResponse.builder()
                .promotionId(p.getPromotionId())
                .branchId(p.getBranchId())
                .name(p.getName())
                .promoCode(p.getPromoCode())
                .type(p.getType())
                .value(p.getValue())
                .minBillTotal(p.getMinBillTotal())
                .startAt(p.getStartAt())
                .endAt(p.getEndAt())
                .priority(p.getPriority())
                .maxUsesTotal(p.getMaxUsesTotal())
                .maxUsesPerCustomer(p.getMaxUsesPerCustomer())
                .isStackable(p.getIsStackable())
                .isActive(p.getIsActive())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .totalRedemptions(redemptionRepository.countByPromotionId(p.getPromotionId()));

        if (includeDetails) {
            builder.items(promotionItemRepository.findByPromotionIdAndIsActiveTrue(p.getPromotionId())
                    .stream().map(this::mapPromotionItem).toList());
            builder.batches(promotionBatchRepository.findByPromotionIdAndIsActiveTrue(p.getPromotionId())
                    .stream().map(this::mapPromotionBatch).toList());
            builder.buyXGetYRules(buyXGetYRuleRepository.findByPromotionIdAndIsActiveTrue(p.getPromotionId())
                    .stream().map(this::mapRule).toList());
        }

        return builder.build();
    }
}
