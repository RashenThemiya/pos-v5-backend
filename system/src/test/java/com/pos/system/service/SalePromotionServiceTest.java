package com.pos.system.service;

import com.pos.system.model.promotion.*;
import com.pos.system.model.sale.*;
import com.pos.system.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalePromotionServiceTest {
    @Mock PromotionRepository promotionRepository;
    @Mock PromotionRedemptionRepository redemptionRepository;
    @Mock OrderPromotionRepository orderPromotionRepository;
    @Mock OrderProductPromotionRepository linePromotionRepository;
    @InjectMocks SalePromotionService service;

    private CustomerOrder order() {
        CustomerOrder order = new CustomerOrder();
        order.setOrderId(10L);
        order.setBranchId(1L);
        order.setCustomerId(3L);
        order.setOrderDate(LocalDateTime.now());
        order.setSubtotal(new BigDecimal("1000"));
        order.setDiscount(new BigDecimal("200"));
        return order;
    }

    private Promotion promotion(long id, String type, String value) {
        Promotion promotion = new Promotion();
        promotion.setPromotionId(id);
        promotion.setBranchId(1L);
        promotion.setName("Saver " + id);
        promotion.setPromoCode("SAVE" + id);
        promotion.setType(type);
        promotion.setValue(new BigDecimal(value));
        promotion.setIsStackable(true);
        when(promotionRepository.findById(id)).thenReturn(Optional.of(promotion));
        return promotion;
    }

    @Test void repeatedLinePromotionAggregatesOneRedemptionAndPreservesSnapshots() {
        promotion(7L, "ITEM_PERCENTAGE", "10");
        List<PromotionRedemption> rows = new ArrayList<>();
        when(redemptionRepository.findByOrderId(10L)).thenAnswer(inv -> rows);
        when(redemptionRepository.save(any())).thenAnswer(inv -> {
            PromotionRedemption row = inv.getArgument(0);
            if (!rows.contains(row)) rows.add(row);
            return row;
        });
        OrderProduct line = new OrderProduct();
        line.setOrderProductId(15L);
        line.setDiscount(new BigDecimal("25"));
        service.recordLine(order(), line, 7L);
        line.setOrderProductId(16L);
        line.setDiscount(new BigDecimal("35"));
        service.recordLine(order(), line, 7L);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDiscountAmount()).isEqualByComparingTo("60");
        assertThat(rows.get(0).getCustomerId()).isEqualTo(3L);
        assertThat(rows.get(0).getPromotionName()).isEqualTo("Saver 7");
        assertThat(service.getAppliedPromotions(10L).get(0).getPromoCode()).isEqualTo("SAVE7");
        verify(linePromotionRepository, times(2)).save(any());
    }

    @Test void stackedBillsUseRunningSubtotalAndDoNotAttributeManualDiscount() {
        promotion(7L, "PERCENTAGE", "10");
        promotion(8L, "PERCENTAGE", "10");
        service.recordBill(order(), List.of(7L, 7L, 8L));
        ArgumentCaptor<OrderPromotion> captor = ArgumentCaptor.forClass(OrderPromotion.class);
        verify(orderPromotionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(OrderPromotion::getDiscountAmount)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("90.00"));
    }

    @Test void attributedDiscountCannotExceedActualInvoiceDiscount() {
        promotion(7L, "FIXED", "500");
        service.recordBill(order(), List.of(7L));
        ArgumentCaptor<PromotionRedemption> captor = ArgumentCaptor.forClass(PromotionRedemption.class);
        verify(redemptionRepository).save(captor.capture());
        assertThat(captor.getValue().getDiscountAmount()).isEqualByComparingTo("200");
    }

    @Test void noPromotionDoesNotTurnManualDiscountIntoRedemption() {
        service.recordBill(order(), List.of());
        OrderProduct line = new OrderProduct();
        line.setDiscount(new BigDecimal("25"));
        service.recordLine(order(), line, null);
        verifyNoInteractions(redemptionRepository, linePromotionRepository, orderPromotionRepository);
    }

    @Test void wrongBranchPromotionIsRejected() {
        promotion(7L, "FIXED", "50").setBranchId(99L);
        assertThatThrownBy(() -> service.recordBill(order(), List.of(7L)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(orderPromotionRepository);
    }
}
