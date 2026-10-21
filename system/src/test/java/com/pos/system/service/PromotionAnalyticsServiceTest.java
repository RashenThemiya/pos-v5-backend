package com.pos.system.service;

import com.pos.system.model.auth.Branch;
import com.pos.system.model.promotion.*;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionAnalyticsServiceTest {
    @Mock BranchRepository branchRepository;
    @Mock PromotionRepository promotionRepository;
    @Mock PromotionRedemptionRepository promotionRedemptionRepository;
    @Mock CustomerOrderRepository orderRepository;
    @InjectMocks PromotionAnalyticsService service;

    private PromotionRedemption redemption(long promotionId, long orderId, String amount, LocalDateTime time) {
        PromotionRedemption row = new PromotionRedemption();
        row.setBranchId(1L);
        row.setPromotionId(promotionId);
        row.setOrderId(orderId);
        row.setDiscountAmount(new BigDecimal(amount));
        row.setPromotionName("Recorded " + promotionId);
        row.setPromotionType("ITEM_FIXED");
        row.setUsedAt(time);
        return row;
    }

    @Test void analyticsUsesRecordedDiscountsKeepsDeletedPromotionsAndCountsOrderRevenueOnce() {
        LocalDateTime now = LocalDateTime.now();
        Branch branch = new Branch();
        branch.setBranchId(1L);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        Promotion edited = new Promotion();
        edited.setPromotionId(7L);
        edited.setName("Renamed promotion");
        edited.setType("PERCENTAGE");
        edited.setIsActive(false);
        when(promotionRepository.findByBranchId(1L)).thenReturn(List.of(edited));
        when(promotionRedemptionRepository.findByBranchId(1L)).thenReturn(List.of(
                redemption(7L, 10L, "60", now), redemption(8L, 10L, "90", now),
                redemption(7L, 11L, "500", now)));
        CustomerOrder completed = new CustomerOrder();
        completed.setOrderId(10L);
        completed.setBranchId(1L);
        completed.setStatus("COMPLETED");
        completed.setOrderDate(now);
        completed.setTotal(new BigDecimal("850"));
        CustomerOrder cancelled = new CustomerOrder();
        cancelled.setOrderId(11L);
        cancelled.setBranchId(1L);
        cancelled.setStatus("CANCELLED");
        when(orderRepository.findAllById(any())).thenReturn(List.of(completed, cancelled));

        var result = service.getAnalytics(1L, now.minusDays(1), now.plusMinutes(1), "30D", "DISCOUNT", 10);
        assertThat(result.getSummary().getRedemptions()).isEqualTo(2);
        assertThat(result.getSummary().getDiscountGiven()).isEqualByComparingTo("150");
        assertThat(result.getSummary().getRevenue()).isEqualByComparingTo("850");
        assertThat(result.getTopPromotions()).extracting(p -> p.getPromotionName())
                .containsExactly("Recorded 8", "Recorded 7");
        assertThat(result.getTypePerformance()).hasSize(1);
        assertThat(result.getTypePerformance().get(0).getType()).isEqualTo("ITEM_FIXED");
        assertThat(result.getTrend().stream().map(bucket -> bucket.getDiscountGiven()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("150");
    }
}
