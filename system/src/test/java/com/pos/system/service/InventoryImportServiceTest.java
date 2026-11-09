package com.pos.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.system.model.auth.Branch;
import com.pos.system.model.catalog.*;
import com.pos.system.model.stock.*;
import com.pos.system.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties={"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","logging.level.root=ERROR","debug=false"})
@ActiveProfiles("test")
@Import({InventoryImportService.class,InitialStockPlanner.class,StockServiceImpl.class,UnitConversionServiceImpl.class,ItemAuditService.class,InventoryImportServiceTest.JsonConfig.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class InventoryImportServiceTest {
    @TestConfiguration static class JsonConfig {
        @Bean ObjectMapper objectMapper(){return new ObjectMapper().findAndRegisterModules();}
    }
    @Autowired InventoryImportService service;
    @Autowired BranchRepository branches;
    @Autowired ItemRepository items;
    @Autowired ItemUnitRepository units;
    @Autowired StockBatchRepository batches;
    @Autowired InventoryImportJobRepository jobs;
    @Autowired ItemVariantRepository variants;
    @Autowired StockCountItemRepository countItems;
    @SpyBean StockMovementRepository movements;
    Long branch;
    Item item;
    ItemUnit unit;

    @BeforeEach void setUp(){
        Branch b=new Branch();b.setName(UUID.randomUUID().toString());branch=branches.save(b).getBranchId();
        item=new Item();item.setBranchId(branch);item.setSku("RICE");item.setName("Rice");item=items.save(item);
        unit=new ItemUnit();unit.setBranchId(branch);unit.setItemId(item.getItemId());unit.setUnitName("KG");unit.setMultiplierToBase(BigDecimal.ONE);unit.setIsBaseUnit(true);unit.setDefaultSellingPrice(BigDecimal.TEN);unit=units.save(unit);
    }
    Map<String,String> opening(String ref){return new LinkedHashMap<>(Map.of("row_reference",ref,"item_sku","RICE","stock_unit","KG","quantity","10","cost_price","2.00","selling_price","3.00"));}
    InventoryImportService.Upload upload(String mode,String sheet,List<Map<String,String>> rows){return new InventoryImportService.Upload("test.xlsx",mode,1,branch,Map.of(sheet,rows));}
    InventoryImportService.View open(){var j=service.validate(branch,upload("OPENING","Opening_Stock",List.of(opening("REF-1"))),"tester");assertThat(j.errors()).isEmpty();return service.confirm(branch,j.id(),"tester",1L);}

    @Test void postingIsIdempotentAndRenamingCannotAddStockAgain(){
        var j=open();service.confirm(branch,j.id(),"tester",1L);
        var renamed=new InventoryImportService.Upload("renamed.xlsx","OPENING",1,branch,Map.of("Opening_Stock",List.of(opening("REF-1"))));
        assertThat(service.validate(branch,renamed,"tester").id()).isEqualTo(j.id());
        assertThat(batches.findByBranchId(branch)).hasSize(1);
    }
    @Test void reusedOpeningReferenceIsRejectedEvenWhenQuantityChanges(){
        open();var row=opening("REF-1");row.put("quantity","11");
        var j=service.validate(branch,upload("OPENING","Opening_Stock",List.of(row)),"tester");
        assertThat(j.status()).isEqualTo("INVALID");assertThat(j.errors().get(0).message()).contains("already posted");
    }
    @Test void stockCountsPreserveSeparateBucketsAndWriteAuditableMovements(){
        open();var row=new LinkedHashMap<>(service.exportCount(branch).get(0));
        row.put("counted_available","7");row.put("counted_damaged","2");row.put("counted_expired","1");row.put("reason","Physical count");
        var j=service.validate(branch,upload("COUNT","Stock_Count",List.of(row)),"tester");assertThat(j.errors()).isEmpty();
        service.confirm(branch,j.id(),"approver",2L);
        var b=batches.findByBranchId(branch).get(0);
        assertThat(b.getQtyRemaining()).isEqualByComparingTo("7");assertThat(b.getAvailableQty()).isEqualByComparingTo("7");assertThat(b.getDamagedQty()).isEqualByComparingTo("2");assertThat(b.getExpiredQty()).isEqualByComparingTo("1");
        assertThat(movements.findByBranchIdOrderByCreatedAtDesc(branch).stream().filter(m->m.getMovementType().startsWith("COUNT_")).toList()).hasSize(3);
    }
    @Test void staleOrTamperedExportsAndBlankCountsAreRejected(){
        open();var row=new LinkedHashMap<>(service.exportCount(branch).get(0));
        assertThat(service.validate(branch,upload("COUNT","Stock_Count",List.of(row)),"tester").status()).isEqualTo("INVALID");
        row.put("counted_available","7");row.put("counted_damaged","0");row.put("counted_expired","0");row.put("reason","Count");row.put("snapshot_available","7");
        assertThat(service.validate(branch,upload("COUNT","Stock_Count",List.of(row)),"tester").errors().get(0).message()).contains("Protected");
        row.put("snapshot_available","10.0000");
        var b=batches.findByBranchId(branch).get(0);b.setAvailableQty(new BigDecimal("9"));b.setQtyRemaining(new BigDecimal("9"));batches.save(b);
        assertThat(service.validate(branch,upload("COUNT","Stock_Count",List.of(row)),"tester").status()).isEqualTo("INVALID");
    }
    @Test void movementFailureRollsBackBatchesAndTheJobRemainsUnposted(){
        var j=service.validate(branch,upload("OPENING","Opening_Stock",List.of(opening("ROLLBACK"))),"tester");
        doThrow(new IllegalStateException("simulated ledger failure")).when(movements).save(any(StockMovement.class));
        assertThatThrownBy(()->service.confirm(branch,j.id(),"tester",1L)).hasMessageContaining("simulated");
        assertThat(batches.findByBranchId(branch)).isEmpty();
        assertThat(jobs.findById(j.id()).orElseThrow().getStatus()).isEqualTo("VALIDATED");
    }
    @Test void catalogCanCreateParentUnitsAndMultiAttributeVariantsTogether(){
        Map<String,String> parent=Map.of("action","CREATE","item_sku","TEE","item_name","T-shirt","base_unit","PCS","base_selling_price","5","is_active","true");
        Map<String,String> variant=Map.of("item_sku","TEE","variant_sku","TEE-RED-M","attribute_name","Color","attribute_value","Red","attribute_2_name","Size","attribute_2_value","M","selling_price","6");
        var j=service.validate(branch,new InventoryImportService.Upload("catalog.xlsx","CATALOG",1,branch,Map.of("Items",List.of(parent),"Variants",List.of(variant))),"tester");
        assertThat(j.errors()).isEmpty();service.confirm(branch,j.id(),"tester",1L);
        var v=variants.findByBranchIdAndSku(branch,"TEE-RED-M").orElseThrow();assertThat(v.getCombinationSignature()).isEqualTo("color=red|size=m");
    }
    @Test void duplicateRowsAndWrongBranchAreBlocked(){
        assertThat(service.validate(branch,upload("OPENING","Opening_Stock",List.of(opening("DUP"),opening("DUP"))),"tester").errors()).hasSize(1);
        assertThatThrownBy(()->service.validate(branch+1,upload("OPENING","Opening_Stock",List.of(opening("X"))),"tester")).hasMessageContaining("branch");
    }
    @Test void invalidNumericInputsAreRejected(){
        assertThatThrownBy(()->InventoryImportService.decimal(Map.of("quantity","1E100"),"quantity",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->InventoryImportService.decimal(Map.of("quantity","-1"),"quantity",true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(InventoryImportService.decimal(Map.of("quantity","0"),"quantity",true)).isEqualByComparingTo("0");
    }
    @Test void alternateUnitsAndDamagedOpeningBalancesReconcileInBaseUnits(){
        var bag=new ItemUnit();bag.setBranchId(branch);bag.setItemId(item.getItemId());bag.setUnitName("BAG");bag.setMultiplierToBase(new BigDecimal("5"));bag.setDefaultSellingPrice(BigDecimal.TEN);units.save(bag);
        var row=opening("BAG-REF");row.put("stock_unit","BAG");row.put("quantity","2");row.put("cost_price","10");row.put("stock_status","DAMAGED");
        var j=service.validate(branch,upload("OPENING","Opening_Stock",List.of(row)),"tester");assertThat(j.errors()).isEmpty();service.confirm(branch,j.id(),"tester",1L);
        var b=batches.findByBranchId(branch).get(0);assertThat(b.getReceivedBaseQty()).isEqualByComparingTo("10");assertThat(b.getDamagedQty()).isEqualByComparingTo("10");assertThat(b.getQtyRemaining()).isZero();
        var ledger=movements.findByBranchIdOrderByCreatedAtDesc(branch);assertThat(ledger).hasSize(2);assertThat(ledger).allSatisfy(m->{assertThat(m.getUnitId()).isEqualTo(unit.getUnitId());assertThat(m.getUnitCost()).isEqualByComparingTo("2");assertThat(m.getRefId()).isEqualTo(j.id());});
    }
    @Test void changesBetweenValidationAndConfirmationAreRolledBackAndReported(){
        open();var row=new LinkedHashMap<>(service.exportCount(branch).get(0));row.put("counted_available","7");row.put("counted_damaged","0");row.put("counted_expired","0");row.put("reason","Count");
        var j=service.validate(branch,upload("COUNT","Stock_Count",List.of(row)),"tester");assertThat(j.errors()).isEmpty();
        var b=batches.findByBranchId(branch).get(0);b.setAvailableQty(new BigDecimal("9"));b.setQtyRemaining(new BigDecimal("9"));batches.save(b);
        assertThatThrownBy(()->service.confirm(branch,j.id(),"tester",1L)).hasMessageContaining("Stock changed");
        assertThat(batches.findById(b.getStockBatchId()).orElseThrow().getAvailableQty()).isEqualByComparingTo("9");
        service.recordFailure(branch,j.id(),"Stock changed");assertThat(service.get(branch,j.id()).status()).isEqualTo("FAILED");
    }
    @Test void weightedCountsRetainFourDecimalPlacesAndAllowExplicitZero(){
        open();var row=new LinkedHashMap<>(service.exportCount(branch).get(0));row.put("counted_available","0.1234");row.put("counted_damaged","0");row.put("counted_expired","0");row.put("reason","Weighed count");
        var j=service.validate(branch,upload("COUNT","Stock_Count",List.of(row)),"tester");service.confirm(branch,j.id(),"tester",1L);
        assertThat(countItems.findAll().stream().filter(c->c.getItemId().equals(item.getItemId())).toList()).singleElement().satisfies(c->assertThat(c.getCountedQty()).isEqualByComparingTo("0.1234"));
    }
    @Test void cancellingDoesNotPostAndReuploadCanRevalidate(){
        var u=upload("OPENING","Opening_Stock",List.of(opening("CANCEL")));var j=service.validate(branch,u,"tester");service.cancel(branch,j.id());
        assertThatThrownBy(()->service.confirm(branch,j.id(),"tester",1L)).hasMessageContaining("Resolve validation");assertThat(batches.findByBranchId(branch)).isEmpty();
        assertThat(service.validate(branch,u,"tester").status()).isEqualTo("VALIDATED");
    }
    Map<String,String> initialRow(String name,String quantity){
        var r=new LinkedHashMap<String,String>();r.put("item_name",name);r.put("quantity",quantity);r.put("cost_price","10");r.put("selling_price","15");r.put("_excel_row","6");return r;
    }
    InventoryImportService.Upload initial(List<Map<String,String>> rows){return new InventoryImportService.Upload("initial.xlsx","INITIAL",2,branch,Map.of("Initial_Stock",rows));}
    @Test void simpleInitialSheetCreatesItemUnitsPricesAndStockInOnePosting(){
        var j=service.validate(branch,initial(List.of(initialRow("Demo Fastener","12"))),"tester");assertThat(j.errors()).isEmpty();
        service.confirm(branch,j.id(),"tester",1L);
        var added=items.findByBranchId(branch).stream().filter(i->i.getName().equals("Demo Fastener")).findFirst().orElseThrow();
        assertThat(added.getSku()).startsWith("IT-");assertThat(added.getCategoryId()).isNotNull();
        var base=units.findByItemIdAndIsBaseUnitTrue(added.getItemId()).orElseThrow();assertThat(base.getUnitName()).isEqualTo("PCS");assertThat(base.getDefaultSellingPrice()).isEqualByComparingTo("15");
        var b=batches.findByBranchIdAndItemId(branch,added.getItemId()).get(0);assertThat(b.getCostPrice()).isEqualByComparingTo("10");assertThat(b.getSellingPrice()).isEqualByComparingTo("15");assertThat(b.getAvailableQty()).isEqualByComparingTo("12");
        var repeated=service.validate(branch,initial(List.of(initialRow("Demo Fastener","12"))),"tester");assertThat(repeated.id()).isEqualTo(j.id());
        var changed=service.validate(branch,initial(List.of(initialRow("Demo Fastener","13"))),"tester");assertThat(changed.status()).isEqualTo("INVALID");
    }
    @Test void initialSheetRollbackRemovesNewCatalogAsWellAsStock(){
        var j=service.validate(branch,initial(List.of(initialRow("Rollback New Item","2"))),"tester");assertThat(j.errors()).isEmpty();
        doThrow(new IllegalStateException("initial ledger failure")).when(movements).save(any(StockMovement.class));
        assertThatThrownBy(()->service.confirm(branch,j.id(),"tester",1L)).hasMessageContaining("initial ledger failure");
        assertThat(items.findByBranchId(branch)).noneMatch(i->i.getName().equals("Rollback New Item"));assertThat(batches.findByBranchId(branch)).isEmpty();
    }
    @Test void initialSheetSupportsVariantsBatchesAndPackPrices(){
        var a=initialRow("Demo Shirt","2");a.put("item_sku","SHIRT");a.put("stock_unit","PACK");a.put("base_unit","PCS");a.put("multiplier_to_base","5");a.put("selling_price","100");a.put("cost_price","50");a.put("attribute_name","Size");a.put("attribute_value","Small");a.put("batch_number","B1");
        var b=new LinkedHashMap<>(a);b.put("attribute_value","Large");b.put("_excel_row","7");
        var j=service.validate(branch,initial(List.of(a,b)),"tester");assertThat(j.errors()).isEmpty();service.confirm(branch,j.id(),"tester",1L);
        var added=items.findByBranchIdAndSku(branch,"SHIRT").orElseThrow();assertThat(variants.findByItemIdOrderByVariantIdAsc(added.getItemId())).hasSize(2).allSatisfy(v->assertThat(v.getDefaultSellingPrice()).isEqualByComparingTo("20"));
        assertThat(batches.findByBranchIdAndItemId(branch,added.getItemId())).hasSize(2).allSatisfy(batch->assertThat(batch.getAvailableQty()).isEqualByComparingTo("10"));
        assertThat(units.findByItemId(added.getItemId()).stream().filter(u->u.getUnitName().equals("PACK")).findFirst().orElseThrow().getDefaultSellingPrice()).isEqualByComparingTo("100");
    }
    @Test void initialZeroCountCreatesCatalogOnlyAndRepeatedRowsAreRejected(){
        var row=initialRow("Empty Item","0");var j=service.validate(branch,initial(List.of(row)),"tester");assertThat(j.errors()).isEmpty();service.confirm(branch,j.id(),"tester",1L);assertThat(batches.findByBranchId(branch)).isEmpty();
        var a=initialRow("Duplicate Item","3");var b=initialRow("Duplicate Item","3");b.put("_excel_row","9");
        var invalid=service.validate(branch,initial(List.of(a,b)),"tester");assertThat(invalid.errors()).anySatisfy(e->{assertThat(e.row()).isEqualTo(9);assertThat(e.message()).contains("Repeated");});
    }
    @Test void initialSheetRejectsIncompleteAttributesMixedVariantsAndConflictingPrices(){
        var incomplete=initialRow("Incomplete","1");incomplete.put("attribute_2_value","Red");
        assertThat(service.validate(branch,initial(List.of(incomplete)),"tester").status()).isEqualTo("INVALID");
        var a=initialRow("Price Conflict","1");a.put("batch_number","A");
        var b=new LinkedHashMap<>(a);b.put("batch_number","B");b.put("selling_price","20");
        assertThat(service.validate(branch,initial(List.of(a,b)),"tester").errors()).anySatisfy(e->assertThat(e.message()).contains("Conflicting base selling prices"));
        var ordinary=initialRow("Mixed","1");var variant=initialRow("Mixed","1");variant.put("attribute_name","Size");variant.put("attribute_value","Small");
        assertThat(service.validate(branch,initial(List.of(ordinary,variant)),"tester").errors()).anySatisfy(e->assertThat(e.message()).contains("Do not mix"));
    }
    InventoryImportService.Upload flat(List<Map<String,String>> rows){return new InventoryImportService.Upload("flat.xlsx","INITIAL",3,branch,Map.of("Initial_Stock",rows));}
    Map<String,String> flatRow(String name){var r=initialRow(name,"4");r.put("category","Hardware");r.put("stock_unit","PCS");r.put("_excel_row","2");return r;}
    @Test void flatInitialCreatesStandardVariantAndItsStockAndCanBeCounted(){
        var j=service.validate(branch,flat(List.of(flatRow("Standard Bolt"))),"tester");assertThat(j.errors()).isEmpty();service.confirm(branch,j.id(),"tester",1L);
        var added=items.findByBranchId(branch).stream().filter(i->i.getName().equals("Standard Bolt")).findFirst().orElseThrow();
        var standard=variants.findByItemIdOrderByVariantIdAsc(added.getItemId()).get(0);assertThat(standard.getCombinationSignature()).isEqualTo("variant=standard");
        assertThat(batches.findByBranchIdAndItemId(branch,added.getItemId())).singleElement().satisfies(b->assertThat(b.getVariantId()).isEqualTo(standard.getVariantId()));
        assertThat(service.validate(branch,flat(List.of(flatRow("Standard Bolt"))),"tester").id()).isEqualTo(j.id());
        var count=new LinkedHashMap<>(service.exportCount(branch).get(0));count.put("counted_available","3");count.put("counted_damaged","0");count.put("counted_expired","0");count.put("reason","Recount");
        var counted=service.validate(branch,upload("COUNT","Stock_Count",List.of(count)),"tester");assertThat(counted.errors()).isEmpty();service.confirm(branch,counted.id(),"tester",1L);
    }
    @Test void flatInitialReusesStandardAndCustomVariantsWithoutPublicSku(){
        var v=new ItemVariant();v.setItemId(item.getItemId());v.setBranchId(branch);v.setVariantSku("INTERNAL-STANDARD");v.setCombinationSignature("variant=standard");v=variants.save(v);
        var row=flatRow("Rice");row.put("item_sku","RICE");row.put("stock_unit","KG");
        var j=service.validate(branch,flat(List.of(row)),"tester");assertThat(j.errors()).isEmpty();service.confirm(branch,j.id(),"tester",1L);
        assertThat(variants.findByItemIdOrderByVariantIdAsc(item.getItemId())).hasSize(1);
        assertThat(variants.findById(v.getVariantId()).orElseThrow().getSku()).isNull();
        var exported=service.exportCount(branch).get(0);assertThat(exported.get("variant_sku")).isEqualTo("INTERNAL-STANDARD");
        var count=new LinkedHashMap<>(exported);count.put("counted_available","0");count.put("counted_damaged","0");count.put("counted_expired","0");count.put("reason","Recount");
        assertThat(service.validate(branch,upload("COUNT","Stock_Count",List.of(count)),"tester").errors()).isEmpty();
        var custom=new ItemVariant();custom.setItemId(item.getItemId());custom.setBranchId(branch);custom.setVariantSku("INTERNAL-GRADE");custom.setCombinationSignature("grade=a");custom=variants.save(custom);
        var customRow=new LinkedHashMap<>(row);customRow.put("attribute_name","Grade");customRow.put("attribute_value","A");customRow.put("batch_number","Second");
        var cj=service.validate(branch,flat(List.of(customRow)),"tester");assertThat(cj.errors()).isEmpty();service.confirm(branch,cj.id(),"tester",1L);
        assertThat(variants.findByItemIdOrderByVariantIdAsc(item.getItemId())).hasSize(2);
        assertThat(batches.findByBranchIdAndItemId(branch,item.getItemId())).anySatisfy(b->assertThat(b.getVariantId()).isNotNull());
    }
    @Test void flatInitialRequiresExplicitCategoryUnitAndCostAndRollsBackNewStandard(){
        for(String key:List.of("category","stock_unit","cost_price","selling_price","quantity","item_name")) {
            var row=flatRow("Missing "+key);row.remove(key);
            assertThat(service.validate(branch,flat(List.of(row)),"tester").status()).isEqualTo("INVALID");
        }
        var j=service.validate(branch,flat(List.of(flatRow("Rollback Flat"))),"tester");assertThat(j.errors()).isEmpty();
        doThrow(new IllegalStateException("flat failure")).when(movements).save(any(StockMovement.class));
        assertThatThrownBy(()->service.confirm(branch,j.id(),"tester",1L)).hasMessageContaining("flat failure");
        assertThat(items.findByBranchId(branch)).noneMatch(i->i.getName().equals("Rollback Flat"));assertThat(batches.findByBranchId(branch)).isEmpty();
    }
    Map<String,String> sharedReferenceVariant(String size,int row){
        var r=flatRow("Same Name Bolt");r.put("item_sku","SHARED-BOLT");r.put("brand","Same Brand");r.put("attribute_name","Size");r.put("attribute_value",size);r.put("row_reference","SHARED-OPENING-DOCUMENT");r.put("_excel_row",Integer.toString(row));return r;
    }
    @Test void sameNameBrandCategoryAndOpeningReferenceAllowDifferentVariantsAndPrices(){
        var a=sharedReferenceVariant("10 mm",2);var b=sharedReferenceVariant("12 mm",3);b.put("selling_price","20");b.put("cost_price","12");
        var j=service.validate(branch,flat(List.of(a,b)),"tester");assertThat(j.errors()).isEmpty();service.confirm(branch,j.id(),"tester",1L);
        var parent=items.findByBranchIdAndSku(branch,"SHARED-BOLT").orElseThrow();
        assertThat(variants.findByItemIdOrderByVariantIdAsc(parent.getItemId())).hasSize(2);
        assertThat(batches.findByBranchIdAndItemId(branch,parent.getItemId())).hasSize(2).allSatisfy(batch->assertThat(batch.getVariantId()).isNotNull());
        assertThat(service.validate(branch,flat(List.of(sharedReferenceVariant("10 mm",2),b)),"tester").id()).isEqualTo(j.id());
        var changed=sharedReferenceVariant("10 mm",2);changed.put("quantity","99");
        var invalid=service.validate(branch,flat(List.of(changed,sharedReferenceVariant("14 mm",3))),"tester");
        assertThat(invalid.errors()).singleElement().satisfies(e->{assertThat(e.row()).isEqualTo(2);assertThat(e.message()).contains("already imported");});
        var next=service.validate(branch,flat(List.of(sharedReferenceVariant("14 mm",2))),"tester");assertThat(next.errors()).isEmpty();service.confirm(branch,next.id(),"tester",1L);
        assertThat(batches.findByBranchIdAndItemId(branch,parent.getItemId())).hasSize(3);
    }
    @Test void repeatedVariantLotIsRejectedEvenWithDifferentEnteredReferences(){
        var a=sharedReferenceVariant("10 mm",2);var duplicate=sharedReferenceVariant("10 mm",17);duplicate.put("row_reference","OTHER-DOCUMENT");
        var invalid=service.validate(branch,flat(List.of(a,duplicate)),"tester");
        assertThat(invalid.errors()).anySatisfy(e->{assertThat(e.row()).isEqualTo(17);assertThat(e.message()).contains("first seen on row 2");});
        var otherBatch=sharedReferenceVariant("10 mm",3);otherBatch.put("batch_number","LOT-B");otherBatch.put("cost_price","11");
        assertThat(service.validate(branch,flat(List.of(a,otherBatch)),"tester").errors()).isEmpty();
    }
    @Test void oneThousandDifferentVariantsCanShareNameBrandCategoryAndDocumentReference(){
        List<Map<String,String>> rows=new ArrayList<>();
        for(int index=0;index<1000;index++)rows.add(sharedReferenceVariant(index+" mm",index+2));
        var j=service.validate(branch,flat(rows),"tester");assertThat(j.errors()).isEmpty();assertThat(j.status()).isEqualTo("VALIDATED");assertThat(j.rowCount()).isEqualTo(1000);
        assertThat(batches.findByBranchId(branch)).isEmpty();
    }
}
