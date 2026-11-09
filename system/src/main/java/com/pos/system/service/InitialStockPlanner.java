package com.pos.system.service;

import com.pos.system.model.catalog.*;
import com.pos.system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import static com.pos.system.service.InventoryImportService.*;

/** Converts the user-facing flat sheet into the existing catalog and stock operations. No writes. */
@Component
@RequiredArgsConstructor
public class InitialStockPlanner {
    private final ItemRepository items;
    private final ItemUnitRepository units;
    private final ItemVariantRepository variants;
    private final CategoryRepository categories;
    private final BrandRepository brands;

    public record Plan(InventoryImportService.Upload catalog, InventoryImportService.Upload opening,
                       List<InventoryImportService.Issue> issues) {}

    public Plan plan(Long branch, InventoryImportService.Upload upload) {
        var issues=new ArrayList<InventoryImportService.Issue>();
        Map<String,Map<String,String>> parents=new LinkedHashMap<>(), unitRows=new LinkedHashMap<>(), variantRows=new LinkedHashMap<>();
        List<Map<String,String>> opening=new ArrayList<>();
        Map<String,Integer> references=new HashMap<>();
        Map<String,Boolean> variantParents=new HashMap<>();
        Map<String,BigDecimal> ordinaryPrices=new HashMap<>();
        for(var entry:upload.sheets().entrySet()) if(!entry.getKey().equals("Initial_Stock")&&!entry.getValue().isEmpty())
            issues.add(new InventoryImportService.Issue(entry.getKey(),1,"Initial stock uses only the Initial Stock sheet"));
        int index=0;
        for(var r:upload.sheets().getOrDefault("Initial_Stock",List.of())) {
            int row=++index+(upload.version()>=3?1:5);try {row=Integer.parseInt(value(r,"_excel_row"));}catch(NumberFormatException ignored){}
            try {
                if(upload.version()>=3) {required(r,"category",100);required(r,"stock_unit",30);required(r,"cost_price",100);}
                String name=required(r,"item_name",200), category=defaultValue(r,"category","General"), brand=value(r,"brand");
                String sku=value(r,"item_sku");
                if(sku.isEmpty()) {
                    var matches=items.findByBranchId(branch).stream().filter(i->i.getName().equalsIgnoreCase(name))
                        .filter(i->value(r,"category").isEmpty()||(i.getCategoryId()!=null&&categories.findById(i.getCategoryId()).map(c->c.getName().equalsIgnoreCase(r.get("category"))).orElse(false)))
                        .filter(i->brand.isEmpty()||(i.getBrandId()!=null&&brands.findById(i.getBrandId()).map(b->b.getName().equalsIgnoreCase(brand)).orElse(false))).toList();
                    if(matches.size()>1)throw new IllegalArgumentException("Several existing items share this name; enter Item Code");
                    sku=matches.isEmpty()?code("IT-",name.toLowerCase(Locale.ROOT)+"|"+category.toLowerCase(Locale.ROOT)+"|"+brand.toLowerCase(Locale.ROOT)):matches.get(0).getSku();
                    r.put("item_sku",sku);
                }
                required(r,"item_sku",100);
                Item existing=items.findByBranchIdAndSkuIgnoreCase(branch,sku).orElse(null);
                String stockUnit=defaultValue(r,"stock_unit","PCS");
                String baseUnit=defaultValue(r,"base_unit",existing==null?stockUnit:units.findByItemIdAndIsBaseUnitTrue(existing.getItemId()).orElseThrow().getUnitName());
                BigDecimal multiplier=value(r,"multiplier_to_base").isEmpty()?BigDecimal.ONE:decimal(r,"multiplier_to_base",true);
                if(multiplier.signum()<=0||multiplier.precision()-multiplier.scale()>8)throw new IllegalArgumentException("Units per Stock Unit must be positive and fit 8 integer / 4 decimal digits");
                if(baseUnit.equalsIgnoreCase(stockUnit)&&multiplier.compareTo(BigDecimal.ONE)!=0)throw new IllegalArgumentException("Units per Stock Unit must be 1 when Stock Unit equals Base Unit");
                if(!baseUnit.equalsIgnoreCase(stockUnit)&&value(r,"multiplier_to_base").isEmpty())throw new IllegalArgumentException("Enter Units per Stock Unit for packs/cartons");
                var qty=decimal(r,"quantity",true);var baseQty=qty.multiply(multiplier).stripTrailingZeros();
                if(baseQty.scale()>4||baseQty.precision()-baseQty.scale()>10)throw new IllegalArgumentException("Converted opening quantity exceeds supported precision");
                if(qty.signum()==0&&value(r,"cost_price").isEmpty())r.put("cost_price","0");
                money(r,"cost_price");var price=money(r,"selling_price");
                String active=defaultValue(r,"is_active",existing==null?"true":existing.getIsActive().toString());
                if(qty.signum()>0&&!active.equalsIgnoreCase("true"))throw new IllegalArgumentException("Positive stock requires an active item; enter TRUE in Active to activate it");
                String weighed=defaultValue(r,"is_weighed",existing==null?"false":existing.getIsWeighed().toString());
                String basePrice=value(r,"base_selling_price").isEmpty()?price.divide(multiplier,2,RoundingMode.HALF_UP).toPlainString():money(r,"base_selling_price").toPlainString();
                Map<String,String> parent=new LinkedHashMap<>(r);
                parent.put("action","UPSERT");parent.put("category",existing!=null&&value(r,"category").isEmpty()?"":category);parent.put("base_unit",baseUnit);parent.put("base_selling_price",basePrice);parent.put("is_active",active);parent.put("is_weighed",weighed);
                // A barcode belongs to an item unit in this system, not to a variant.
                parent.put("base_barcode",baseUnit.equalsIgnoreCase(stockUnit)?value(r,"barcode"):"");
                String parentKey=sku.toLowerCase(Locale.ROOT);
                var prior=parents.get(parentKey);
                if(prior==null)parents.put(parentKey,parent);
                else {
                    for(String key:List.of("item_name","category","brand","base_unit","is_active","is_weighed","minimum_stock","maximum_stock","scale_item_code","scale_barcode_prefix","base_barcode")) {
                        String a=value(prior,key),b=value(parent,key);
                        if(!a.isEmpty()&&!b.isEmpty()&&!a.equalsIgnoreCase(b))throw new IllegalArgumentException("Conflicting "+key+" for repeated Item Code "+sku);
                        if(a.isEmpty()&&!b.isEmpty())prior.put(key,b);
                    }
                }
                String unitKey=parentKey+"|"+stockUnit.toLowerCase(Locale.ROOT);
                if(!baseUnit.equalsIgnoreCase(stockUnit)) {
                    var unit=new LinkedHashMap<String,String>();unit.put("item_sku",sku);unit.put("unit_name",stockUnit);unit.put("multiplier_to_base",multiplier.toPlainString());unit.put("selling_price",price.toPlainString());unit.put("barcode",value(r,"barcode"));unit.put("is_active","true");unit.put("_excel_row",Integer.toString(row));
                    var old=unitRows.putIfAbsent(unitKey,unit);
                    if(old!=null&&(!old.get("multiplier_to_base").equals(unit.get("multiplier_to_base"))||!value(old,"barcode").equals(value(unit,"barcode"))))throw new IllegalArgumentException("Conflicting unit conversion/barcode for "+stockUnit);
                }
                String variantSku=value(r,"variant_sku");
                var existingVariants=existing==null?List.<ItemVariant>of():variants.findByItemIdOrderByVariantIdAsc(existing.getItemId());
                boolean hasAttributes=false;
                Set<String> attributeNames=new HashSet<>();
                for(String prefix:List.of("attribute","attribute_2","attribute_3")) {
                    String attributeName=value(r,prefix+"_name"), attributeValue=value(r,prefix+"_value");
                    if(attributeName.isEmpty()!=attributeValue.isEmpty())throw new IllegalArgumentException("Each variant attribute requires both a name and a value");
                    if(!attributeName.isEmpty()) {
                        hasAttributes=true;
                        if(!attributeNames.add(attributeName.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Repeated variant attribute name");
                    }
                }
                if(hasAttributes&&value(r,"attribute_name").isEmpty())throw new IllegalArgumentException("Enter the first variant attribute before additional attributes");
                boolean customVariant=hasAttributes||!variantSku.isEmpty();
                if(!hasAttributes&&variantSku.isEmpty()&&!Boolean.parseBoolean(weighed)) {
                    var activeVariants=existingVariants.stream().filter(v->Boolean.TRUE.equals(v.getIsActive())).toList();
                    if(activeVariants.size()==1&&"variant=standard".equalsIgnoreCase(activeVariants.get(0).getCombinationSignature())) {
                        variantSku=variantCode(activeVariants.get(0));r.put("variant_sku",variantSku);
                        r.put("attribute_name","Variant");r.put("attribute_value","Standard");hasAttributes=true;
                    } else if(!activeVariants.isEmpty())throw new IllegalArgumentException("This item has variants; enter Variant Code or its attributes");
                    else if(existing==null&&upload.version()>=3) {
                        r.put("attribute_name","Variant");r.put("attribute_value","Standard");hasAttributes=true;
                    }
                }
                if(hasAttributes&&variantSku.isEmpty()) {
                    SortedMap<String,String> attributes=new TreeMap<>();
                    for(String prefix:List.of("attribute","attribute_2","attribute_3"))if(!value(r,prefix+"_name").isEmpty())attributes.put(value(r,prefix+"_name").toLowerCase(Locale.ROOT),value(r,prefix+"_value").toLowerCase(Locale.ROOT));
                    String signature=attributes.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("|"));
                    var matching=existingVariants.stream().filter(v->signature.equalsIgnoreCase(v.getCombinationSignature())).findFirst();
                    variantSku=matching.map(InventoryImportService::variantCode).orElseGet(()->code("VR-",parentKey+"|"+attributes));r.put("variant_sku",variantSku);
                }
                boolean variantRow=customVariant&&!("Variant".equalsIgnoreCase(value(r,"attribute_name"))&&"Standard".equalsIgnoreCase(value(r,"attribute_value")));
                var priorVariant=variantParents.putIfAbsent(parentKey,variantRow);
                if(priorVariant!=null&&priorVariant!=variantRow)throw new IllegalArgumentException("Do not mix ordinary stock and variant stock for the same item");
                if(!variantRow) {
                    var priorPrice=ordinaryPrices.putIfAbsent(parentKey,new BigDecimal(basePrice));
                    if(priorPrice!=null&&priorPrice.compareTo(new BigDecimal(basePrice))!=0)throw new IllegalArgumentException("Conflicting base selling prices for the same item; use one base price for all its batches");
                    var priorUnitPrice=ordinaryPrices.putIfAbsent("unit:"+unitKey,price);
                    if(priorUnitPrice!=null&&priorUnitPrice.compareTo(price)!=0)throw new IllegalArgumentException("Conflicting selling prices for the same item unit");
                }
                if(!variantSku.isEmpty()) {
                    Map<String,String> v=new LinkedHashMap<>(r);v.put("selling_price",basePrice);v.put("is_active",active);
                    String key=variantSku.toLowerCase(Locale.ROOT);var old=variantRows.putIfAbsent(key,v);
                    if(old!=null)for(String field:List.of("item_sku","selling_price","attribute_name","attribute_value","attribute_2_name","attribute_2_value","attribute_3_name","attribute_3_value"))
                        if(!value(old,field).equalsIgnoreCase(value(v,field)))throw new IllegalArgumentException("Conflicting variant details/prices for "+variantSku);
                } else if(existing!=null&&existingVariants.stream().anyMatch(v->Boolean.TRUE.equals(v.getIsActive())))
                    throw new IllegalArgumentException("This item has variants; enter Variant Code or its attributes");
                String condition=defaultValue(r,"stock_status","AVAILABLE").toUpperCase(Locale.ROOT);
                if(!Set.of("AVAILABLE","DAMAGED","EXPIRED").contains(condition))throw new IllegalArgumentException("Stock Condition must be AVAILABLE, DAMAGED or EXPIRED");
                if(!value(r,"expiry_date").isEmpty()) {
                    var date=LocalDate.parse(r.get("expiry_date"));
                    if(qty.signum()>0&&condition.equals("AVAILABLE")&&date.isBefore(LocalDate.now()))throw new IllegalArgumentException("Past-expiry stock must be entered as EXPIRED");
                }
                for(String key:List.of("batch_number","supplier_batch_barcode"))if(value(r,key).length()>255)throw new IllegalArgumentException(key+" exceeds 255 characters");
                String ref=value(r,"row_reference");
                if(ref.isEmpty())r.put("row_reference",openingReference(r));
                required(r,"row_reference",100);
                // An entered reference can identify the whole opening/count document.
                // Scope the posting identity to its item/variant/lot, not that reference alone.
                String postingReference=openingReference(r);
                var firstRow=references.putIfAbsent(openingIdentity(r),row);
                if(firstRow!=null)throw new IllegalArgumentException("Repeated stock row for the same item, variant, unit, batch, expiry and condition (first seen on row "+firstRow+"); combine its quantity or enter different variant/batch details. Different variants may share the same name, brand, category and Opening Reference");
                if(qty.signum()>0) {
                    var line=new LinkedHashMap<>(r);line.put("stock_unit",stockUnit);line.put("stock_status",condition);line.put("row_reference",postingReference);opening.add(line);
                }
            }catch(Exception e){issues.add(new InventoryImportService.Issue("Initial Stock",row,e.getMessage()));}
        }
        var catalog=new InventoryImportService.Upload(upload.fileName(),"CATALOG",1,branch,Map.of("Items",new ArrayList<>(parents.values()),"Units",new ArrayList<>(unitRows.values()),"Variants",new ArrayList<>(variantRows.values())));
        var stock=new InventoryImportService.Upload(upload.fileName(),"OPENING",1,branch,Map.of("Opening_Stock",opening));
        return new Plan(catalog,stock,issues);
    }
    /** Stable for repeated planning, job retries, and older jobs with generated references. */
    public String openingReference(Map<String,String> row) {
        String identity=openingIdentity(row);
        String generated=code("INITIAL-",identity), entered=value(row,"row_reference");
        // Preserve existing auto-generated identifiers; never hash the output repeatedly.
        return entered.isEmpty()||entered.equalsIgnoreCase(generated)?generated:code("INITIAL-",identity+"|source:"+entered.toLowerCase(Locale.ROOT));
    }
    private String openingIdentity(Map<String,String> row) {
        return value(row,"item_sku").toLowerCase(Locale.ROOT)+"|"+value(row,"variant_sku").toLowerCase(Locale.ROOT)+"|"+defaultValue(row,"stock_unit","PCS").toLowerCase(Locale.ROOT)+"|"+value(row,"batch_number").toLowerCase(Locale.ROOT)+"|"+value(row,"expiry_date")+"|"+defaultValue(row,"stock_status","AVAILABLE").toUpperCase(Locale.ROOT);
    }
    private String defaultValue(Map<String,String> row,String key,String fallback){return value(row,key).isEmpty()?fallback:value(row,key);}
    private String code(String prefix,String seed){try{return prefix+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8))).substring(0,20).toUpperCase(Locale.ROOT);}catch(Exception e){throw new IllegalStateException(e);}}
}
