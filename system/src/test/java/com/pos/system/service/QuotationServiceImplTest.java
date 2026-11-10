package com.pos.system.service;

import com.pos.system.dto.sale.*;
import com.pos.system.model.auth.Branch;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.sale.Quotation;
import com.pos.system.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql=false, properties={"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","logging.level.root=ERROR","debug=false"})
@ActiveProfiles("test")
@Import(QuotationServiceImpl.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class QuotationServiceImplTest {
    @Autowired QuotationService service;
    @Autowired BranchRepository branches;
    @Autowired ItemRepository items;
    @Autowired QuotationRepository quotations;
    @Autowired QuotationItemRepository lines;
    @MockBean UserRepository users;
    Long branchId, firstItem, secondItem;

    @BeforeEach void setup() {
        when(users.existsById(anyLong())).thenReturn(true);
        Branch branch=new Branch();branch.setName("Quotation test "+UUID.randomUUID());branchId=branches.save(branch).getBranchId();
        firstItem=item("First");secondItem=item("Second");
    }
    private Long item(String name) {
        Item item=new Item();item.setBranchId(branchId);item.setSku(UUID.randomUUID().toString());item.setName(name);return items.save(item).getItemId();
    }
    private QuotationItemRequest line(Long itemId,String price) {
        var line=new QuotationItemRequest();line.setItemId(itemId);line.setQuantity(BigDecimal.TEN);line.setUnitPrice(new BigDecimal(price));return line;
    }
    private QuotationRequest request() {
        var r=new QuotationRequest();r.setBranchId(branchId);r.setUserId(1L);r.setItems(List.of(line(firstItem,"100"),line(secondItem,"535")));return r;
    }
    @Test void initialQuotationHasNonNullAmountsBeforeItsIdentityInsert() {
        var q=new Quotation();assertThat(List.of(q.getSubtotal(),q.getItemDiscount(),q.getDiscount(),q.getTaxAmount(),q.getRounding(),q.getTotal())).allSatisfy(amount->assertThat(amount).isZero());
    }
    @Test void createsQuotationWithExplicitZeroDiscountsAndCorrect6350Total() {
        var r=request();r.setDiscount(BigDecimal.ZERO);r.setTaxAmount(BigDecimal.ZERO);r.setRounding(BigDecimal.ZERO);r.getItems().forEach(l->l.setDiscount(BigDecimal.ZERO));
        var result=service.create(r);
        assertThat(result.getQuotationId()).isNotNull();assertThat(result.getStatus()).isEqualTo("DRAFT");
        var stored=quotations.findById(result.getQuotationId()).orElseThrow();
        assertThat(stored.getSubtotal()).isEqualByComparingTo("6350");assertThat(stored.getItemDiscount()).isZero();assertThat(stored.getTotal()).isEqualByComparingTo("6350");
        assertThat(lines.findByQuotationIdOrderByQuotationItemId(result.getQuotationId())).hasSize(2);
    }
    @Test void missingOptionalAmountsDefaultToZero() {
        var result=service.create(request());
        assertThat(result.getItemDiscount()).isZero();assertThat(result.getDiscount()).isZero();assertThat(result.getTaxAmount()).isZero();assertThat(result.getRounding()).isZero();assertThat(result.getTotal()).isEqualByComparingTo("6350");
    }
    @Test void calculatesFinalDiscountsTaxAndRoundingRatherThanKeepingInitialZeros() {
        var r=request();r.getItems().get(0).setDiscount(new BigDecimal("100"));r.setDiscount(new BigDecimal("50"));r.setTaxAmount(new BigDecimal("20"));r.setRounding(new BigDecimal("0.50"));
        var result=service.create(r);var stored=quotations.findById(result.getQuotationId()).orElseThrow();
        assertThat(stored.getItemDiscount()).isEqualByComparingTo("100");assertThat(stored.getDiscount()).isEqualByComparingTo("50");assertThat(stored.getTotal()).isEqualByComparingTo("6220.50");
    }
    @Test void invalidLaterItemRollsBackHeaderAndEarlierItems() {
        var r=request();r.getItems().get(1).setQuantity(BigDecimal.ZERO);
        long before=lines.count();
        assertThatThrownBy(()->service.create(r)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity");
        assertThat(quotations.findAll()).noneMatch(q->branchId.equals(q.getBranchId()));assertThat(lines.count()).isEqualTo(before);
    }
}
