package com.pos.system.service;

import com.pos.system.dto.promotion.*;
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

@Service
@RequiredArgsConstructor
@Transactional
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionItemRepository promotionItemRepository;
    private final PromotionBatchRepository promotionBatchRepository;
    private final PromotionBuyXGetYRuleRepository buyXGetYRuleRepository;
    private final PromotionRedemptionRepository redemptionRepository;

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

        // if promo code changed, check uniqueness
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
        return mapPromotion(findPromoById(promotionId), true);
    }

    @Override
    public List<PromotionResponse> getPromotionsByBranch(Long branchId) {
        return promotionRepository.findByBranchId(branchId)
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
        findPromoById(promotionId);

        promotionItemRepository.findByPromotionIdAndItemId(promotionId, request.getItemId())
                .ifPresent(x -> {
                    throw new RuntimeException("Item already linked to this promotion");
                });

        PromotionItem item = new PromotionItem();
        item.setBranchId(request.getBranchId());
        item.setPromotionId(promotionId);
        item.setItemId(request.getItemId());
        item.setMaxQty(request.getMaxQty());
        item.setUsedQty(BigDecimal.ZERO);
        item.setIsActive(true);

        return mapPromotionItem(promotionItemRepository.save(item));
    }

    @Override
    public void removeItemFromPromotion(Long promotionId, Long itemId) {
        PromotionItem item = promotionItemRepository.findByPromotionIdAndItemId(promotionId, itemId)
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
        findPromoById(promotionId);

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
        findPromoById(promotionId);

        PromotionBuyXGetYRule rule = new PromotionBuyXGetYRule();
        rule.setBranchId(request.getBranchId());
        rule.setPromotionId(promotionId);
        rule.setBuyItemId(request.getBuyItemId());
        rule.setBuyQty(request.getBuyQty());
        rule.setGetItemId(request.getGetItemId());
        rule.setGetQty(request.getGetQty());
        rule.setGetDiscountPercent(
                request.getGetDiscountPercent() != null ? request.getGetDiscountPercent() : BigDecimal.valueOf(100)
        );

        return mapRule(buyXGetYRuleRepository.save(rule));
    }

    @Override
    public void removeBuyXGetYRule(Long ruleId) {
        if (!buyXGetYRuleRepository.existsById(ruleId)) {
            throw new RuntimeException("Rule not found: " + ruleId);
        }
        buyXGetYRuleRepository.deleteById(ruleId);
    }

    @Override
    public List<BuyXGetYRuleResponse> getRulesByPromotion(Long promotionId) {
        return buyXGetYRuleRepository.findByPromotionId(promotionId)
                .stream().map(this::mapRule).toList();
    }

    // ─── Apply Promotion ─────────────────────────────────────────────────────────

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
                // promo not applicable - skip
            }
        }

        // sort by discount amount descending
        results.sort((a, b) -> b.getDiscountAmount().compareTo(a.getDiscountAmount()));
        return results;
    }

    // ─── Core discount calculation ───────────────────────────────────────────────

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
            throw new RuntimeException("Bill total does not meet minimum requirement of " + promo.getMinBillTotal());
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
                throw new RuntimeException("Customer has reached the max uses for this promotion");
            }
        }
    }

    private ApplyPromotionResponse calculateDiscount(Promotion promo, ApplyPromotionRequest request) {
        BigDecimal discountAmount = BigDecimal.ZERO;
        List<ApplyPromotionResponse.LineDiscountDto> lineDiscounts = new ArrayList<>();

        switch (promo.getType()) {

            case "PERCENTAGE" -> {
                // % off the entire bill
                discountAmount = request.getBillTotal()
                        .multiply(promo.getValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }

            case "FIXED" -> {
                // flat amount off bill, capped at bill total
                discountAmount = promo.getValue().min(request.getBillTotal());
            }

            case "ITEM_PERCENTAGE" -> {
                // % off specific items
                List<PromotionItem> promoItems = promotionItemRepository.findByPromotionIdAndIsActiveTrue(promo.getPromotionId());
                for (ApplyPromotionRequest.CartItemDto cartItem : request.getCartItems()) {
                    promoItems.stream()
                            .filter(pi -> pi.getItemId().equals(cartItem.getItemId()))
                            .findFirst()
                            .ifPresent(pi -> {
                                BigDecimal lineTotal = cartItem.getUnitPrice().multiply(cartItem.getQty());
                                BigDecimal lineDiscount = lineTotal
                                        .multiply(promo.getValue())
                                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                                lineDiscounts.add(ApplyPromotionResponse.LineDiscountDto.builder()
                                        .itemId(cartItem.getItemId())
                                        .discountAmount(lineDiscount)
                                        .reason(promo.getValue() + "% off")
                                        .build());
                            });
                }
                discountAmount = lineDiscounts.stream()
                        .map(ApplyPromotionResponse.LineDiscountDto::getDiscountAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            case "ITEM_FIXED" -> {
                // fixed amount off specific items
                List<PromotionItem> promoItems = promotionItemRepository.findByPromotionIdAndIsActiveTrue(promo.getPromotionId());
                for (ApplyPromotionRequest.CartItemDto cartItem : request.getCartItems()) {
                    promoItems.stream()
                            .filter(pi -> pi.getItemId().equals(cartItem.getItemId()))
                            .findFirst()
                            .ifPresent(pi -> {
                                BigDecimal lineTotal = cartItem.getUnitPrice().multiply(cartItem.getQty());
                                BigDecimal lineDiscount = promo.getValue().min(lineTotal);
                                lineDiscounts.add(ApplyPromotionResponse.LineDiscountDto.builder()
                                        .itemId(cartItem.getItemId())
                                        .discountAmount(lineDiscount)
                                        .reason("Fixed " + promo.getValue() + " off")
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
                    // check if buy item is in cart with sufficient qty
                    boolean buyQtyMet = request.getCartItems().stream()
                            .filter(c -> c.getItemId().equals(rule.getBuyItemId()))
                            .anyMatch(c -> c.getQty().compareTo(rule.getBuyQty()) >= 0);

                    if (buyQtyMet) {
                        // find the free/discounted item in the cart
                        request.getCartItems().stream()
                                .filter(c -> c.getItemId().equals(rule.getGetItemId()))
                                .findFirst()
                                .ifPresent(getItem -> {
                                    BigDecimal discountableQty = getItem.getQty().min(rule.getGetQty());
                                    BigDecimal lineDiscount = getItem.getUnitPrice()
                                            .multiply(discountableQty)
                                            .multiply(rule.getGetDiscountPercent())
                                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                                    lineDiscounts.add(ApplyPromotionResponse.LineDiscountDto.builder()
                                            .itemId(rule.getGetItemId())
                                            .discountAmount(lineDiscount)
                                            .reason("Buy " + rule.getBuyQty() + " Get " + rule.getGetQty() + " (" + rule.getGetDiscountPercent() + "% off)")
                                            .build());
                                });
                    }
                }
                discountAmount = lineDiscounts.stream()
                        .map(ApplyPromotionResponse.LineDiscountDto::getDiscountAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            case "BATCH" -> {
                // discount for items from specific stock batches (by barcode)
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

    // ─── Private helpers ─────────────────────────────────────────────────────────

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

    // ─── Mappers ─────────────────────────────────────────────────────────────────

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
                .buyQty(r.getBuyQty())
                .getItemId(r.getGetItemId())
                .getQty(r.getGetQty())
                .getDiscountPercent(r.getGetDiscountPercent())
                .build();
    }
}
