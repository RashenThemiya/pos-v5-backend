package com.pos.system.service;

import com.pos.system.dto.promotion.*;

import java.util.List;

public interface PromotionService {

    // Promotion CRUD
    PromotionResponse createPromotion(PromotionRequest request);
    PromotionResponse updatePromotion(Long promotionId, PromotionRequest request);
    PromotionResponse getPromotionById(Long promotionId);
    List<PromotionResponse> getPromotionsByBranch(Long branchId);
    List<PromotionResponse> getActivePromotionsByBranch(Long branchId);
    void togglePromotion(Long promotionId, boolean isActive);

    // Items
    PromotionItemResponse addItemToPromotion(Long promotionId, PromotionItemRequest request);
    void removeItemFromPromotion(Long promotionId, Long itemId);
    List<PromotionItemResponse> getItemsByPromotion(Long promotionId);

    // Batches
    PromotionBatchResponse addBatchToPromotion(Long promotionId, PromotionBatchRequest request);
    void removeBatchFromPromotion(Long promotionId, Long batchId);
    List<PromotionBatchResponse> getBatchesByPromotion(Long promotionId);

    // BuyXGetY Rules
    BuyXGetYRuleResponse addBuyXGetYRule(Long promotionId, BuyXGetYRuleRequest request);
    void removeBuyXGetYRule(Long ruleId);
    List<BuyXGetYRuleResponse> getRulesByPromotion(Long promotionId);

    // Apply (called by sales module at checkout)
    ApplyPromotionResponse applyPromotion(Long promotionId, ApplyPromotionRequest request);
    ApplyPromotionResponse applyPromotionByCode(ApplyPromotionRequest request);
    List<ApplyPromotionResponse> getApplicablePromotions(ApplyPromotionRequest request);
}
