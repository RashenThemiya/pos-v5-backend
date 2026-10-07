package com.pos.system.controller;

import com.pos.system.dto.promotion.*;
import com.pos.system.dto.promotion.PromotionAnalyticsResponse.*;
import com.pos.system.service.PromotionAnalyticsService;
import com.pos.system.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
@CrossOrigin
public class PromotionController {

    private final PromotionService promotionService;
    private final PromotionAnalyticsService promotionAnalyticsService;

    // ─── Promotion CRUD ──────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_CREATE')")
    @PostMapping
    public ResponseEntity<PromotionResponse> createPromotion(@RequestBody PromotionRequest request) {
        return ResponseEntity.ok(promotionService.createPromotion(request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_UPDATE')")
    @PutMapping("/{promotionId}")
    public ResponseEntity<PromotionResponse> updatePromotion(@PathVariable Long promotionId,
                                                             @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(promotionService.updatePromotion(promotionId, request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW')")
    @GetMapping("/{promotionId}")
    public ResponseEntity<PromotionResponse> getPromotionById(@PathVariable Long promotionId) {
        return ResponseEntity.ok(promotionService.getPromotionById(promotionId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW')")
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<PromotionResponse>> getPromotionsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(promotionService.getPromotionsByBranch(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW')")
    @GetMapping("/branch/{branchId}/item/{itemId}")
    public ResponseEntity<List<PromotionResponse>> getPromotionsByItem(
            @PathVariable Long branchId, @PathVariable Long itemId) {
        return ResponseEntity.ok(promotionService.getPromotionsByItem(branchId, itemId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW')")
    @GetMapping("/branch/{branchId}/active")
    public ResponseEntity<List<PromotionResponse>> getActivePromotionsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(promotionService.getActivePromotionsByBranch(branchId));
    }

    // Analytics

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}")
    public ResponseEntity<PromotionAnalyticsResponse> getPromotionAnalytics(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange,
            @RequestParam(defaultValue = "REVENUE") String sortBy,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(promotionAnalyticsService.getAnalytics(
                branchId, from, to, dateRange, sortBy, limit));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/summary")
    public ResponseEntity<Summary> getPromotionAnalyticsSummary(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(promotionAnalyticsService.getSummary(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/trend")
    public ResponseEntity<List<TrendBucket>> getPromotionAnalyticsTrend(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(promotionAnalyticsService.getTrend(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/type-performance")
    public ResponseEntity<List<TypePerformance>> getPromotionTypePerformance(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(promotionAnalyticsService.getTypePerformance(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/status")
    public ResponseEntity<StatusBreakdown> getPromotionStatus(@PathVariable Long branchId) {
        return ResponseEntity.ok(promotionAnalyticsService.getStatus(branchId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/top-promotions")
    public ResponseEntity<List<TopPromotion>> getTopPromotions(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange,
            @RequestParam(defaultValue = "REVENUE") String sortBy,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(promotionAnalyticsService.getTopPromotions(
                branchId, from, to, dateRange, sortBy, limit));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @GetMapping("/analytics/branch/{branchId}/notifications")
    public ResponseEntity<List<Notification>> getPromotionNotifications(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(promotionAnalyticsService.getNotifications(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_UPDATE') or hasAuthority('DASHBOARD_VIEW')")
    @PatchMapping("/analytics/branch/{branchId}/notifications/mark-all-read")
    public ResponseEntity<NotificationReadResponse> markPromotionNotificationsRead(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "30D") String dateRange) {

        return ResponseEntity.ok(promotionAnalyticsService.markAllNotificationsRead(branchId, from, to, dateRange));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_UPDATE')")
    @PatchMapping("/{promotionId}/activate")
    public ResponseEntity<Void> activatePromotion(@PathVariable Long promotionId) {
        promotionService.togglePromotion(promotionId, true);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_UPDATE')")
    @PatchMapping("/{promotionId}/deactivate")
    public ResponseEntity<Void> deactivatePromotion(@PathVariable Long promotionId) {
        promotionService.togglePromotion(promotionId, false);
        return ResponseEntity.ok().build();
    }

    // ─── Items ───────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_UPDATE')")
    @PostMapping("/{promotionId}/items")
    public ResponseEntity<PromotionItemResponse> addItem(@PathVariable Long promotionId,
                                                         @RequestBody PromotionItemRequest request) {
        return ResponseEntity.ok(promotionService.addItemToPromotion(promotionId, request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_UPDATE')")
    @DeleteMapping("/{promotionId}/items/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long promotionId,
                                           @PathVariable Long itemId) {
        promotionService.removeItemFromPromotion(promotionId, itemId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW')")
    @GetMapping("/{promotionId}/items")
    public ResponseEntity<List<PromotionItemResponse>> getItems(@PathVariable Long promotionId) {
        return ResponseEntity.ok(promotionService.getItemsByPromotion(promotionId));
    }

    // ─── Batches ─────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_UPDATE')")
    @PostMapping("/{promotionId}/batches")
    public ResponseEntity<PromotionBatchResponse> addBatch(@PathVariable Long promotionId,
                                                           @RequestBody PromotionBatchRequest request) {
        return ResponseEntity.ok(promotionService.addBatchToPromotion(promotionId, request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_UPDATE')")
    @DeleteMapping("/{promotionId}/batches/{batchId}")
    public ResponseEntity<Void> removeBatch(@PathVariable Long promotionId,
                                            @PathVariable Long batchId) {
        promotionService.removeBatchFromPromotion(promotionId, batchId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW')")
    @GetMapping("/{promotionId}/batches")
    public ResponseEntity<List<PromotionBatchResponse>> getBatches(@PathVariable Long promotionId) {
        return ResponseEntity.ok(promotionService.getBatchesByPromotion(promotionId));
    }

    // ─── BuyXGetY Rules ──────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_UPDATE')")
    @PostMapping("/{promotionId}/rules")
    public ResponseEntity<BuyXGetYRuleResponse> addRule(@PathVariable Long promotionId,
                                                        @RequestBody BuyXGetYRuleRequest request) {
        return ResponseEntity.ok(promotionService.addBuyXGetYRule(promotionId, request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_UPDATE')")
    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> removeRule(@PathVariable Long ruleId) {
        promotionService.removeBuyXGetYRule(ruleId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_VIEW')")
    @GetMapping("/{promotionId}/rules")
    public ResponseEntity<List<BuyXGetYRuleResponse>> getRules(@PathVariable Long promotionId) {
        return ResponseEntity.ok(promotionService.getRulesByPromotion(promotionId));
    }

    // ─── Apply ───────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_APPLY')")
    @PostMapping("/{promotionId}/apply")
    public ResponseEntity<ApplyPromotionResponse> applyPromotion(@PathVariable Long promotionId,
                                                                 @RequestBody ApplyPromotionRequest request) {
        return ResponseEntity.ok(promotionService.applyPromotion(promotionId, request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_APPLY')")
    @PostMapping("/apply-by-code")
    public ResponseEntity<ApplyPromotionResponse> applyByCode(@RequestBody ApplyPromotionRequest request) {
        return ResponseEntity.ok(promotionService.applyPromotionByCode(request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PROMOTION_APPLY')")
    @PostMapping("/applicable")
    public ResponseEntity<List<ApplyPromotionResponse>> getApplicable(@RequestBody ApplyPromotionRequest request) {
        return ResponseEntity.ok(promotionService.getApplicablePromotions(request));
    }
}
