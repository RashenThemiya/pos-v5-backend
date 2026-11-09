package com.pos.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.system.model.catalog.*;
import com.pos.system.model.stock.*;
import com.pos.system.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryImportService {
    private final InventoryImportJobRepository jobs;
    private final ItemRepository items;
    private final ItemVariantRepository variants;
    private final ItemVariantAttributeRepository attributes;
    private final ItemUnitRepository units;
    private final StockBatchRepository batches;
    private final StockMovementRepository movements;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final ScaleItemMappingRepository scaleMappings;
    private final StockCountRepository counts;
    private final StockCountItemRepository countItems;
    private final StockRepository stocks;
    private final BrandCategoryRepository brandCategories;
    private final ItemAuditService itemAudit;
    private final UnitMasterRepository masterUnits;
    private final StockService stockService;
    private final EntityManager em;
    private final ObjectMapper mapper;
    private final InitialStockPlanner initialStock;

    public record Upload(String fileName, String mode, int version, Long branchId, Map<String, List<Map<String,String>>> sheets) {}
    public record Issue(String sheet, int row, String message) {}
    public record View(Long id, String fileName, String mode, String status, String uploadedBy,
                       LocalDateTime createdAt, int rowCount, List<Issue> errors, Map<String,List<Map<String,String>>> preview) {}

    @Transactional
    public View validate(Long branch, Upload upload, String actor) {
        if (("INITIAL".equals(upload.mode())?!Set.of(2,3).contains(upload.version()):upload.version()!=1) || !Objects.equals(branch, upload.branchId())) fail("Workbook version or branch does not match");
        if (upload.mode()==null || !Set.of("INITIAL", "CATALOG", "OPENING", "COUNT").contains(upload.mode())) fail("Unknown import mode");
        if (upload.sheets() == null) fail("Workbook sheets are required");
        if(em.find(com.pos.system.model.auth.Branch.class,branch,LockModeType.PESSIMISTIC_WRITE)==null)fail("Branch not found");
        int count = upload.sheets().values().stream().mapToInt(List::size).sum();
        if (count == 0 || count > 10000) fail("Upload must contain between 1 and 10,000 rows");
        Map<String,List<Map<String,String>>> normalized=new TreeMap<>();
        upload.sheets().forEach((name, data)->normalized.put(name,data.stream().map(r->{Map<String,String> clean=new TreeMap<>();r.forEach((k,v)->clean.put(k,value(r,k)));return clean;}).toList()));
        upload=new Upload(upload.fileName(),upload.mode(),upload.version(),upload.branchId(),normalized);
        if("INITIAL".equals(upload.mode())) initialStock.plan(branch,upload); // Persist generated identities before hashing/retries.
        String payload = json(upload);
        if (payload.length() > 10_000_000) fail("Workbook exceeds 10 MB of data");
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(List.of(upload.mode(),normalized)).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
        var previous = jobs.findByBranchIdAndFileHash(branch, hash);
        if (previous.isPresent()) {
            var existing=previous.get();
            if(Set.of("INVALID","FAILED","CANCELLED").contains(existing.getStatus())) {var issues=inspect(branch,upload);existing.setErrors(json(issues));existing.setStatus(issues.isEmpty()?"VALIDATED":"INVALID");jobs.save(existing);}
            return view(existing);
        }
        List<Issue> errors = inspect(branch, upload);
        InventoryImportJob job = new InventoryImportJob();
        job.setBranchId(branch); job.setFileHash(hash); job.setFileName(upload.fileName()); job.setMode(upload.mode());
        job.setStatus(errors.isEmpty() ? "VALIDATED" : "INVALID"); job.setUploadedBy(actor);
        job.setCreatedAt(LocalDateTime.now()); job.setPayload(payload); job.setErrors(json(errors)); job.setRowCount(count);
        return view(jobs.save(job));
    }

    public List<View> history(Long branch) { return jobs.findTop100ByBranchIdOrderByCreatedAtDesc(branch).stream().filter(j->!"SNAPSHOT".equals(j.getMode())).map(this::view).toList(); }
    public View get(Long branch, Long id) { return view(job(branch, id)); }
    public Map<String,Object> report(Long branch,Long id) {
        var job=job(branch,id);if("SNAPSHOT".equals(job.getMode()))fail("Select an import job");
        return Map.of("job",view(job),"sheets",read(job.getPayload(),Upload.class).sheets(),"postedBy",Objects.toString(job.getPostedBy(),""),"postedAt",Objects.toString(job.getPostedAt(),""));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordFailure(Long branch,Long id,String reason) {
        em.find(com.pos.system.model.auth.Branch.class,branch,LockModeType.PESSIMISTIC_WRITE);
        var job=job(branch,id);
        em.lock(job,LockModeType.PESSIMISTIC_WRITE);em.refresh(job);
        if(!"POSTED".equals(job.getStatus())) {job.setStatus("FAILED");job.setErrors(json(List.of(new Issue("Posting",0,"All changes rolled back: "+reason))));jobs.save(job);}
    }

    @Transactional
    public View cancel(Long branch,Long id) {
        em.find(com.pos.system.model.auth.Branch.class,branch,LockModeType.PESSIMISTIC_WRITE);
        var job=job(branch,id);em.lock(job,LockModeType.PESSIMISTIC_WRITE);
        if("SNAPSHOT".equals(job.getMode()))fail("Count export snapshots cannot be cancelled");
        if("POSTED".equals(job.getStatus()))fail("Posted inventory cannot be cancelled; use a new stock count to correct it");
        job.setStatus("CANCELLED");return view(jobs.save(job));
    }

    @Transactional(rollbackFor = Exception.class)
    public View confirm(Long branch, Long id, String actor, Long userId) {
        // The branch row serializes inventory imports; the job lock makes retries idempotent.
        em.find(com.pos.system.model.auth.Branch.class, branch, LockModeType.PESSIMISTIC_WRITE);
        InventoryImportJob job = job(branch, id);
        em.lock(job, LockModeType.PESSIMISTIC_WRITE);
        if ("POSTED".equals(job.getStatus())) return view(job);
        if (!Set.of("VALIDATED","FAILED").contains(job.getStatus())) fail("Resolve validation errors before posting");
        Upload upload = read(job.getPayload(), Upload.class);
        List<Issue> errors = inspect(branch, upload);
        if (!errors.isEmpty()) fail("Data changed since validation: " + errors.get(0).message());
        if ("CATALOG".equals(upload.mode())) postCatalog(branch, upload, id);
        if ("OPENING".equals(upload.mode())) postOpening(branch, upload, userId, id);
        if ("COUNT".equals(upload.mode())) postCount(branch, upload, userId, id);
        if ("INITIAL".equals(upload.mode())) {
            var plan=initialStock.plan(branch,upload);
            postCatalog(branch,plan.catalog(),id);
            if(!rows(plan.opening(),"Opening_Stock").isEmpty()) {
                var stockErrors=inspect(branch,plan.opening());
                if(!stockErrors.isEmpty())fail("Initial stock: "+stockErrors.get(0).message());
                postOpening(branch,plan.opening(),userId,id);
            }
        }
        em.flush();
        job.setStatus("POSTED"); job.setErrors("[]"); job.setPostedBy(actor); job.setPostedAt(LocalDateTime.now());
        return view(jobs.save(job));
    }

    private List<Issue> inspect(Long branch, Upload upload) {
        if("INITIAL".equals(upload.mode())) {
            var plan=initialStock.plan(branch,upload);
            List<Issue> issues=new ArrayList<>(plan.issues());
            inspect(branch,plan.catalog()).forEach(e->issues.add(new Issue("Initial Stock",e.row(),e.message())));
            var posted=postedOpeningReferences(branch);
            for(var row:rows(plan.opening(),"Opening_Stock"))if(posted.contains(value(row,"row_reference").toLowerCase(Locale.ROOT)))issues.add(new Issue("Initial Stock",Integer.parseInt(value(row,"_excel_row").isEmpty()?"6":value(row,"_excel_row")),"This opening item/variant/batch was already imported; use a stock count to correct an existing balance"));
            return issues;
        }
        List<Issue> errors = new ArrayList<>(); Set<String> seen = new HashSet<>();
        Map<Long,Map<String,Map<String,String>>> snapshots=new HashMap<>();
        Set<String> openingReferences=upload.mode().equals("OPENING")?postedOpeningReferences(branch):Set.of();
        Set<String> allowed = switch (upload.mode()) { case "CATALOG" -> Set.of("Items", "Units", "Variants"); case "OPENING" -> Set.of("Opening_Stock"); default -> Set.of("Stock_Count"); };
        upload.sheets().forEach((sheet, rows) -> {
            if (!allowed.contains(sheet) && !rows.isEmpty()) { errors.add(new Issue(sheet, 1, "Sheet is not allowed for this mode")); return; }
            for (int n=0; n<rows.size(); n++) {
                var r = rows.get(n);
                try {
                    String sku = required(r,"item_sku",100);
                    String key = sheet + ":" + sku.toLowerCase(Locale.ROOT);
                    if (sheet.equals("Units")) key += ":" + required(r,"unit_name",30).toLowerCase(Locale.ROOT);
                    if (sheet.equals("Variants")) key = sheet + ":" + required(r,"variant_sku",100).toLowerCase(Locale.ROOT);
                    if (sheet.equals("Opening_Stock")) key = sheet + ":" + required(r,"row_reference",100).toLowerCase(Locale.ROOT);
                    if (sheet.equals("Stock_Count")) key = sheet + ":" + required(r,"batch_barcode",120).toLowerCase(Locale.ROOT);
                    if (!seen.add(key)) fail("Duplicate row identifier");
                    if (sheet.equals("Items")) {
                        required(r,"item_name",200); required(r,"base_unit",30);
                        money(r,"base_selling_price"); bool(r,"is_active");
                        if(!value(r,"is_weighed").isEmpty()) bool(r,"is_weighed");
                        for(String limit:List.of("minimum_stock","maximum_stock"))if(!value(r,limit).isEmpty()) {var quantity=decimal(r,limit,true);if(quantity.scale()>3||quantity.precision()-quantity.scale()>9)fail(limit+" supports 9 integer digits and 3 decimal places");}
                        if(!value(r,"category").isEmpty()) required(r,"category",100);
                        if(!value(r,"brand").isEmpty()) required(r,"brand",100);
                        String action = required(r,"action",20);
                        if (!Set.of("CREATE","UPDATE","UPSERT").contains(action)) fail("Action must be CREATE, UPDATE or UPSERT");
                        var existing = items.findByBranchIdAndSkuIgnoreCase(branch,sku);
                        BigDecimal minimum=value(r,"minimum_stock").isEmpty()?existing.map(Item::getMinStock).orElse(null):decimal(r,"minimum_stock",true);
                        BigDecimal maximum=value(r,"maximum_stock").isEmpty()?existing.map(Item::getMaxStock).orElse(null):decimal(r,"maximum_stock",true);
                        if(minimum!=null&&maximum!=null&&minimum.compareTo(maximum)>0)fail("Minimum stock exceeds maximum stock");
                        var skuVariant=variants.findByBranchIdAndSkuIgnoreCase(branch,sku);
                        if(skuVariant.isPresent()&&(existing.isEmpty()||!skuVariant.get().getItemId().equals(existing.get().getItemId()))) fail("Item SKU conflicts with a variant SKU");
                        if(value(r,"is_weighed").equalsIgnoreCase("true")) {
                            required(r,"scale_item_code",30); required(r,"scale_barcode_prefix",10);
                            if(!seen.add("scale:"+r.get("scale_item_code"))) fail("Duplicate scale item code");
                            var mapping=scaleMappings.findByBranchIdAndScaleItemCode(branch,r.get("scale_item_code"));
                            if(mapping.isPresent()&&(existing.isEmpty()||!mapping.get().getItemId().equals(existing.get().getItemId()))) fail("Scale code already in use");
                        }
                        validateBarcode(branch,r,"base_barcode",existing.map(Item::getItemId).orElse(null),null,seen);
                        if (action.equals("CREATE") && existing.isPresent()) fail("Item already exists");
                        if (action.equals("UPDATE") && existing.isEmpty()) fail("Item does not exist");
                        if (existing.isPresent()) {
                            var base = units.findByItemIdAndIsBaseUnitTrue(existing.get().getItemId()).orElseThrow();
                            if (!base.getUnitName().equalsIgnoreCase(r.get("base_unit"))) fail("Base unit cannot be changed by import");
                            if(!value(r,"is_weighed").isEmpty() && bool(r,"is_weighed")!=Boolean.TRUE.equals(existing.get().getIsWeighed())) fail("Existing weighed-item configuration must be changed through the item editor");
                        }
                    } else if (sheet.equals("Units") || sheet.equals("Variants")) {
                        Item item = catalogItem(branch,sku,upload);
                        if (sheet.equals("Units")) {
                            BigDecimal factor=decimal(r,"multiplier_to_base",true); if (factor.signum()<=0) fail("Multiplier must be positive");
                            if(factor.precision()-factor.scale()>8)fail("Unit conversion exceeds supported precision");
                            money(r,"selling_price");
                            if(!value(r,"is_active").isEmpty())bool(r,"is_active");
                            var existing=item.getItemId()==null?Optional.<ItemUnit>empty():units.findByItemId(item.getItemId()).stream().filter(u->u.getUnitName().equalsIgnoreCase(r.get("unit_name"))).findFirst();
                            if(existing.isPresent() && existing.get().getMultiplierToBase().compareTo(factor)!=0) fail("Existing unit conversion cannot be changed");
                            validateBarcode(branch,r,"barcode",item.getItemId(),existing.map(ItemUnit::getUnitId).orElse(null),seen);
                            String base=item.getItemId()==null?rows(upload,"Items").stream().filter(x->sku.equalsIgnoreCase(x.get("item_sku"))).findFirst().orElseThrow().get("base_unit"):units.findByItemIdAndIsBaseUnitTrue(item.getItemId()).orElseThrow().getUnitName();
                            if(base.equalsIgnoreCase(r.get("unit_name"))&&factor.compareTo(BigDecimal.ONE)!=0) fail("Base unit multiplier must be 1");
                            if(base.equalsIgnoreCase(r.get("unit_name"))&&value(r,"is_active").equalsIgnoreCase("false"))fail("Deactivate the item instead of its base unit");
                        } else {
                            var v=importVariant(branch,r.get("variant_sku"));
                            String signature=hasAttributes(r)?signature(r):v.map(ItemVariant::getCombinationSignature).orElseThrow(()->new IllegalArgumentException("New variants require attributes")); money(r,"selling_price");
                            if(!value(r,"is_active").isEmpty())bool(r,"is_active");
                            if(Boolean.TRUE.equals(item.getIsWeighed())) fail("Weighed items cannot have variants");
                            if(v.isEmpty()&&(items.findByBranchIdAndSkuIgnoreCase(branch,r.get("variant_sku")).isPresent()||rows(upload,"Items").stream().anyMatch(x->r.get("variant_sku").equalsIgnoreCase(x.get("item_sku"))))) fail("Variant SKU conflicts with an item SKU");
                            if(v.isPresent() && !v.get().getItemId().equals(item.getItemId())) fail("Variant belongs to another item");
                            if(v.isPresent()&&!signature.equalsIgnoreCase(v.get().getCombinationSignature())&&(batches.existsByVariantId(v.get().getVariantId())||movements.existsByVariantId(v.get().getVariantId())))fail("Cannot change attributes of a variant with stock history; create a new variant SKU");
                            if(v.isEmpty()&&item.getItemId()!=null&&batches.findByBranchIdAndItemId(branch,item.getItemId()).stream().anyMatch(b->b.getVariantId()==null&&(available(b).signum()>0||nvl(b.getDamagedQty()).signum()>0||nvl(b.getExpiredQty()).signum()>0)))fail("Item has stock without a variant; reconcile that stock before adding variants");
                            if(!seen.add("combination:"+sku.toLowerCase(Locale.ROOT)+":"+signature)) fail("Duplicate variant combination");
                            if(item.getItemId()!=null && variants.findByItemIdOrderByVariantIdAsc(item.getItemId()).stream().anyMatch(x->!Objects.equals(x.getVariantId(),v.map(ItemVariant::getVariantId).orElse(null))&&signature.equalsIgnoreCase(x.getCombinationSignature()))) fail("Attribute combination already belongs to another variant");
                        }
                    } else if(sheet.equals("Opening_Stock")) {
                        Item item=item(branch,sku); resolveVariant(branch,item,r); var unit=resolveUnit(item,required(r,"stock_unit",30));
                        if(decimal(r,"quantity",true).signum()<=0) fail("Opening quantity must be positive");
                        var baseQty=decimal(r,"quantity",true).multiply(unit.getMultiplierToBase()).stripTrailingZeros();
                        if(baseQty.scale()>4||baseQty.precision()-baseQty.scale()>10)fail("Converted base quantity exceeds supported precision");
                        required(r,"row_reference",100);
                        if(value(r,"batch_number").length()>255)fail("Batch number exceeds 255 characters");
                        money(r,"cost_price"); money(r,"selling_price");
                        if(openingReferences.contains(value(r,"row_reference").toLowerCase(Locale.ROOT))) fail("Opening row reference already posted; use the original job result");
                        String condition=value(r,"stock_status").isEmpty()?"AVAILABLE":value(r,"stock_status");
                        if(!Set.of("AVAILABLE","DAMAGED","EXPIRED").contains(condition)) fail("Unknown stock condition");
                        if(condition.equals("AVAILABLE")&&!value(r,"expiry_date").isEmpty() && LocalDate.parse(r.get("expiry_date")).isBefore(LocalDate.now())) fail("Expired stock must have EXPIRED condition");
                    } else if(sheet.equals("Stock_Count")) {
                        Item item=items.findByBranchIdAndSkuIgnoreCase(branch,sku).orElseThrow(()->new IllegalArgumentException("Unknown item SKU"));
                        StockBatch b=batch(branch,required(r,"batch_barcode",120));
                        if(!b.getItemId().equals(item.getItemId())) fail("Batch belongs to another item");
                        String expected=b.getVariantId()==null?"":variantCode(variants.findById(b.getVariantId()).orElseThrow());
                        if(!expected.equals(value(r,"variant_sku"))) fail("Batch variant mismatch");
                        decimal(r,"snapshot_available",true); decimal(r,"snapshot_damaged",true); decimal(r,"snapshot_expired",true);
                        if(!value(r,"counted_available").isEmpty()) decimal(r,"counted_available",true);
                        if(!value(r,"counted_damaged").isEmpty()) decimal(r,"counted_damaged",true);
                        if(!value(r,"counted_expired").isEmpty()) decimal(r,"counted_expired",true);
                        if(value(r,"counted_available").isEmpty() || value(r,"counted_damaged").isEmpty() || value(r,"counted_expired").isEmpty()) fail("Enter all three counts; use 0 for none");
                        if(value(r,"reason").isEmpty()) fail("Count reason is required");
                        required(r,"reason",180);
                        if(b.getExpiryDate()!=null&&b.getExpiryDate().isBefore(LocalDate.now())&&decimal(r,"counted_available",true).signum()>0)fail("Expired batches cannot be counted as available");
                        verifyExport(branch,r,snapshots);
                        checkSnapshot(b,r);
                    }
                } catch(Exception e) { int excelRow=n+2;try {excelRow=Integer.parseInt(value(r,"_excel_row"));}catch(NumberFormatException ignored){} errors.add(new Issue(sheet,excelRow,e.getMessage())); }
            }
        });
        return errors;
    }

    private void postCatalog(Long branch, Upload upload, Long job) {
        for(var r: rows(upload,"Items")) {
            Item item=items.findByBranchIdAndSkuIgnoreCase(branch,r.get("item_sku")).orElseGet(Item::new);
            boolean fresh=item.getItemId()==null;
            item.setBranchId(branch); item.setSku(r.get("item_sku")); item.setName(r.get("item_name")); item.setIsActive(bool(r,"is_active"));
            if(!value(r,"is_weighed").isEmpty()) item.setIsWeighed(bool(r,"is_weighed"));
            if(!value(r,"minimum_stock").isEmpty()) item.setMinStock(decimal(r,"minimum_stock",true));
            if(!value(r,"maximum_stock").isEmpty()) item.setMaxStock(decimal(r,"maximum_stock",true));
            if(!value(r,"category").isEmpty()) {
                Category category=categories.findByBranchId(branch).stream().filter(c->c.getName().equalsIgnoreCase(r.get("category"))).findFirst().orElseGet(()->{
                    Category c=new Category();c.setBranchId(branch);c.setName(r.get("category"));c.setIsActive(true);c.setCreatedAt(LocalDateTime.now());return categories.save(c);
                }); item.setCategoryId(category.getCategoryId());
            }
            if(!value(r,"brand").isEmpty()) {
                Brand brand=brands.findByBranchId(branch).stream().filter(b->b.getName().equalsIgnoreCase(r.get("brand"))).findFirst().orElseGet(()->{
                    Brand b=new Brand();b.setBranchId(branch);b.setName(r.get("brand"));b.setIsActive(true);b.setCreatedAt(LocalDateTime.now());return brands.save(b);
                });item.setBrandId(brand.getBrandId());
            }
            if(!value(r,"scale_barcode_prefix").isEmpty()) item.setScaleBarcodePrefix(r.get("scale_barcode_prefix"));
            item.setUpdatedAt(LocalDateTime.now()); if(fresh) item.setCreatedAt(LocalDateTime.now()); items.save(item);
            if(item.getCategoryId()!=null&&item.getBrandId()!=null&&!brandCategories.existsByBrandIdAndCategoryId(item.getBrandId(),item.getCategoryId())) {
                brandCategories.save(new BrandCategory(null,branch,item.getBrandId(),item.getCategoryId(),LocalDateTime.now()));
            }
            itemAudit.record(branch,item.getItemId(),fresh?"CREATE":"UPDATE","ITEM",item.getItemId(),"Inventory import #"+job);
            ItemUnit unit=units.findByItemIdAndIsBaseUnitTrue(item.getItemId()).orElseGet(ItemUnit::new);
            unit.setBranchId(branch); unit.setItemId(item.getItemId()); unit.setUnitName(r.get("base_unit")); unit.setIsBaseUnit(true);
            unit.setMasterUnitId(masterUnit(branch,r.get("base_unit")));
            unit.setMultiplierToBase(BigDecimal.ONE); unit.setDefaultSellingPrice(decimal(r,"base_selling_price",true)); unit.setIsActive(true);
            if(!value(r,"base_barcode").isEmpty()) unit.setBarcode(r.get("base_barcode"));
            unit.setUpdatedAt(LocalDateTime.now()); if(fresh) unit.setCreatedAt(LocalDateTime.now()); units.save(unit);
            if(Boolean.TRUE.equals(item.getIsWeighed())&&!value(r,"scale_item_code").isEmpty()) {
                ScaleItemMapping mapping=scaleMappings.findByItemIdAndIsActiveTrue(item.getItemId()).orElseGet(()->scaleMappings.findByBranchIdAndScaleItemCode(branch,r.get("scale_item_code")).orElseGet(ScaleItemMapping::new));
                mapping.setBranchId(branch);mapping.setItemId(item.getItemId());mapping.setUnitId(unit.getUnitId());mapping.setScaleItemCode(r.get("scale_item_code"));mapping.setIsActive(true);mapping.setCreatedAt(LocalDateTime.now());scaleMappings.save(mapping);
            }
        }
        for(var r:rows(upload,"Units")) {
            Item item=items.findByBranchIdAndSkuIgnoreCase(branch,r.get("item_sku")).orElseThrow();
            ItemUnit unit=units.findByItemId(item.getItemId()).stream().filter(u->u.getUnitName().equalsIgnoreCase(r.get("unit_name"))).findFirst().orElseGet(ItemUnit::new);
            unit.setBranchId(branch); unit.setItemId(item.getItemId()); unit.setUnitName(r.get("unit_name")); unit.setMultiplierToBase(decimal(r,"multiplier_to_base",true));
            unit.setMasterUnitId(masterUnit(branch,r.get("unit_name")));unit.setUpdatedAt(LocalDateTime.now());if(unit.getCreatedAt()==null)unit.setCreatedAt(LocalDateTime.now());
            if(!value(r,"barcode").isEmpty())unit.setBarcode(r.get("barcode")); unit.setDefaultSellingPrice(decimal(r,"selling_price",true));if(!value(r,"is_active").isEmpty())unit.setIsActive(bool(r,"is_active")); units.save(unit);
            itemAudit.record(branch,item.getItemId(),"UPSERT","ITEM_UNIT",unit.getUnitId(),"Inventory import #"+job);
        }
        for(var r:rows(upload,"Variants")) {
            Item item=items.findByBranchIdAndSkuIgnoreCase(branch,r.get("item_sku")).orElseThrow();
            ItemVariant v=importVariant(branch,r.get("variant_sku")).orElseGet(ItemVariant::new);
            v.setBranchId(branch); v.setItemId(item.getItemId());
            if(v.getVariantId()==null) {v.setSku(r.get("variant_sku")); v.setVariantSku(r.get("variant_sku"));}
            if(hasAttributes(r))v.setCombinationSignature(signature(r)); v.setDefaultSellingPrice(decimal(r,"selling_price",true));if(!value(r,"is_active").isEmpty())v.setIsActive(bool(r,"is_active")); v.setUpdatedAt(LocalDateTime.now()); if(v.getCreatedAt()==null)v.setCreatedAt(LocalDateTime.now()); variants.save(v);
            if(hasAttributes(r)) {
            attributes.deleteByVariantId(v.getVariantId()); em.flush();
            attributes.save(new ItemVariantAttribute(null,v.getVariantId(),r.get("attribute_name"),r.get("attribute_value")));
            for(int index=2;index<=20;index++) if(!value(r,"attribute_"+index+"_name").isEmpty()) attributes.save(new ItemVariantAttribute(null,v.getVariantId(),r.get("attribute_"+index+"_name"),r.get("attribute_"+index+"_value")));
            }
            itemAudit.record(branch,item.getItemId(),"UPSERT","ITEM_VARIANT",v.getVariantId(),"Inventory import #"+job);
        }
    }

    private void postOpening(Long branch,Upload upload,Long user,Long job) {
        var dto=new com.pos.system.dto.stock.OpeningStockRequestDto(); dto.setBranchId(branch); dto.setCreatedBy(user);
        var lines=new ArrayList<com.pos.system.dto.stock.OpeningStockItemRequestDto>();
        for(var r:rows(upload,"Opening_Stock")) {
            Item i=item(branch,r.get("item_sku")); var line=new com.pos.system.dto.stock.OpeningStockItemRequestDto();
            line.setItemId(i.getItemId()); line.setVariantId(resolveVariant(branch,i,r)); line.setUnitId(resolveUnit(i,r.get("stock_unit")).getUnitId());
            line.setQuantity(decimal(r,"quantity",true)); line.setCostPrice(decimal(r,"cost_price",true)); line.setSellingPrice(decimal(r,"selling_price",true));
            line.setBatchNo(value(r,"batch_number")); if(!value(r,"expiry_date").isEmpty()) line.setExpiryDate(LocalDate.parse(r.get("expiry_date")));
            line.setSupplierBatchBarcode(value(r,"supplier_batch_barcode"));
            line.setNote("Inventory import #"+job+" / "+r.get("row_reference")); lines.add(line);
        }
        dto.setItems(lines); stockService.addOpeningStock(dto);
        var openingMovements=movements.findByBranchIdAndNoteStartingWith(branch,"Inventory import #"+job+" / ");
        for(var movement:openingMovements) {
            var inputUnit=units.findById(movement.getUnitId()).orElseThrow();
            movement.setUnitCost(movement.getUnitCost().divide(inputUnit.getMultiplierToBase(),2,java.math.RoundingMode.HALF_UP));
            movement.setUnitPrice(movement.getUnitPrice().divide(inputUnit.getMultiplierToBase(),2,java.math.RoundingMode.HALF_UP));
            movement.setUnitId(units.findByItemIdAndIsBaseUnitTrue(movement.getItemId()).orElseThrow().getUnitId());
            movement.setRefTable("inventory_import_jobs");movement.setRefId(job);movements.save(movement);
        }
        Map<String,StockMovement> byReference=new HashMap<>();openingMovements.forEach(m->byReference.put(m.getNote(),m));
        for(var r:rows(upload,"Opening_Stock")) {
            String condition=value(r,"stock_status");
            if(condition.equals("DAMAGED")||condition.equals("EXPIRED")) {
                // Locate by the durable import reference on its OPENING movement, not by list ordering.
                var movement=Objects.requireNonNull(byReference.get("Inventory import #"+job+" / "+r.get("row_reference")));
                StockBatch b=batch(branch,movement.getInternalBatchBarcode());
                if(condition.equals("DAMAGED")) b.setDamagedQty(b.getAvailableQty()); else b.setExpiredQty(b.getAvailableQty());
                b.setAvailableQty(BigDecimal.ZERO);b.setQtyRemaining(BigDecimal.ZERO);batches.save(b);
                StockMovement reclass=new StockMovement();reclass.setBranchId(branch);reclass.setItemId(b.getItemId());reclass.setVariantId(b.getVariantId());reclass.setUnitId(movement.getUnitId());reclass.setUnitCost(movement.getUnitCost());reclass.setInternalBatchBarcode(b.getInternalBatchBarcode());reclass.setMovementType("AVAILABLE_TO_"+condition);reclass.setQuantity(movement.getQuantity());reclass.setRefTable("inventory_import_jobs");reclass.setRefId(job);reclass.setCreatedBy(user);reclass.setCreatedAt(LocalDateTime.now());reclass.setNote("Opening stock condition from import #"+job);movements.save(reclass);
            }
        }
    }

    private void postCount(Long branch,Upload upload,Long user,Long job) {
        StockCount count=new StockCount();count.setBranchId(branch);count.setCreatedBy(user);count.setApprovedBy(user);count.setStatus("POSTED");count.setCountDate(LocalDateTime.now());count.setNote("Inventory import #"+job);counts.save(count);
        Map<String,StockCountItem> totals=new LinkedHashMap<>();
        for(var r:rows(upload,"Stock_Count")) {
            StockBatch b=batch(branch,r.get("batch_barcode")); em.lock(b,LockModeType.PESSIMISTIC_WRITE); em.refresh(b); checkSnapshot(b,r);
            BigDecimal available=decimal(r,"counted_available",true), damaged=decimal(r,"counted_damaged",true), expired=decimal(r,"counted_expired",true);
            BigDecimal oldAvailable=available(b),oldDamaged=nvl(b.getDamagedQty()),oldExpired=nvl(b.getExpiredQty());
            b.setAvailableQty(available); b.setDamagedQty(damaged); b.setExpiredQty(expired); b.setQtyRemaining(available); batches.save(b);
            String countKey=b.getItemId()+":"+b.getVariantId();
            StockCountItem ci=totals.computeIfAbsent(countKey,key->{StockCountItem line=new StockCountItem();line.setStockCountId(count.getStockCountId());line.setItemId(b.getItemId());line.setVariantId(b.getVariantId());line.setSystemQty(BigDecimal.ZERO);line.setCountedQty(BigDecimal.ZERO);line.setNote("Inventory import #"+job);return line;});
            ci.setSystemQty(ci.getSystemQty().add(oldAvailable));ci.setCountedQty(ci.getCountedQty().add(available));ci.setDifferenceQty(ci.getCountedQty().subtract(ci.getSystemQty()));
            var stock=stocks.findByBranchIdAndItemIdAndVariantId(branch,b.getItemId(),b.getVariantId());stock.ifPresent(s->{s.setLastUpdated(LocalDateTime.now());stocks.save(s);});
            String[] conditions={"AVAILABLE","DAMAGED","EXPIRED"};BigDecimal[] before={oldAvailable,oldDamaged,oldExpired};BigDecimal[] after={available,damaged,expired};
            for(int c=0;c<3;c++) {
            BigDecimal difference=after[c].subtract(before[c]);
            StockMovement m=new StockMovement(); m.setBranchId(branch); m.setMovementType("COUNT_"+conditions[c]); m.setItemId(b.getItemId()); m.setVariantId(b.getVariantId());
            m.setUnitId(units.findByItemIdAndIsBaseUnitTrue(b.getItemId()).orElseThrow().getUnitId()); m.setInternalBatchBarcode(b.getInternalBatchBarcode());
            if(b.getCostPrice()!=null)m.setUnitCost(b.getCostPrice().divide(units.findById(b.getUnitId()).orElseThrow().getMultiplierToBase(),2,java.math.RoundingMode.HALF_UP));
            m.setQuantity(difference); m.setRefTable("inventory_import_jobs"); m.setRefId(job); m.setCreatedBy(user); m.setCreatedAt(LocalDateTime.now());
            m.setNote((before[c].toPlainString()+" -> "+after[c].toPlainString()+"; "+r.get("reason"))); movements.save(m);
            }
        }
        countItems.saveAll(totals.values());
    }

    @Transactional
    public List<Map<String,String>> exportCount(Long branch) {
        InventoryImportJob snapshot=new InventoryImportJob();snapshot.setBranchId(branch);snapshot.setMode("SNAPSHOT");snapshot.setFileHash(UUID.randomUUID().toString());snapshot.setStatus("EXPORTED");snapshot.setCreatedAt(LocalDateTime.now());snapshot.setErrors("[]");snapshot.setRowCount(0);jobs.save(snapshot);
        List<Map<String,String>> result=batches.findByBranchId(branch).stream().map(b->{
            Item i=items.findById(b.getItemId()).orElseThrow(); Map<String,String> r=new LinkedHashMap<>();
            r.put("item_sku",i.getSku()); r.put("item_name",i.getName()); r.put("variant_sku",b.getVariantId()==null?"":variantCode(variants.findById(b.getVariantId()).orElseThrow()));
            r.put("batch_barcode",b.getInternalBatchBarcode()); r.put("batch_number",b.getBatchNo()); r.put("expiry_date",b.getExpiryDate()==null?"":b.getExpiryDate().toString());
            r.put("base_unit",units.findByItemIdAndIsBaseUnitTrue(i.getItemId()).orElseThrow().getUnitName());
            r.put("category",i.getCategoryId()==null?"":categories.findById(i.getCategoryId()).map(Category::getName).orElse(""));r.put("brand",i.getBrandId()==null?"":brands.findById(i.getBrandId()).map(Brand::getName).orElse(""));
            r.put("snapshot_id",snapshot.getId().toString());r.put("snapshot_version",Objects.toString(b.getVersion(),"0"));
            r.put("snapshot_available",available(b).toPlainString()); r.put("snapshot_damaged",nvl(b.getDamagedQty()).toPlainString()); r.put("snapshot_expired",nvl(b.getExpiredQty()).toPlainString());
            r.put("counted_available",""); r.put("counted_damaged",""); r.put("counted_expired",""); r.put("reason",""); return r;
        }).toList();snapshot.setPayload(json(result));snapshot.setRowCount(result.size());jobs.save(snapshot);return result;
    }
    private void checkSnapshot(StockBatch b,Map<String,String> r) {
        if(!Objects.toString(b.getVersion(),"0").equals(value(r,"snapshot_version"))||available(b).compareTo(decimal(r,"snapshot_available",true))!=0 || nvl(b.getDamagedQty()).compareTo(decimal(r,"snapshot_damaged",true))!=0 || nvl(b.getExpiredQty()).compareTo(decimal(r,"snapshot_expired",true))!=0) fail("Stock changed since export; export and recount this batch");
    }
    private Long resolveVariant(Long branch,Item item,Map<String,String> r) {
        if(value(r,"variant_sku").isEmpty()) {
            if(variants.findByItemIdOrderByVariantIdAsc(item.getItemId()).stream().anyMatch(v->Boolean.TRUE.equals(v.getIsActive()))) fail("Variant SKU is required");
            return null;
        }
        var v=importVariant(branch,r.get("variant_sku")).orElseThrow(()->new IllegalArgumentException("Unknown variant"));
        if(!v.getItemId().equals(item.getItemId()) || !Boolean.TRUE.equals(v.getIsActive())) fail("Variant is inactive or belongs to another item"); return v.getVariantId();
    }
    private Optional<ItemVariant> importVariant(Long branch,String code) {
        var sku=variants.findByBranchIdAndSkuIgnoreCase(branch,code);
        var internal=variants.findByBranchIdAndVariantSkuIgnoreCase(branch,code);
        if(sku.isPresent()&&internal.isPresent()&&!sku.get().getVariantId().equals(internal.get().getVariantId()))fail("Ambiguous variant code");
        return sku.isPresent()?sku:internal;
    }
    static String variantCode(ItemVariant variant) {return variant.getSku()==null||variant.getSku().isBlank()?variant.getVariantSku():variant.getSku();}
    private Item catalogItem(Long branch,String sku,Upload upload) {
        var existing=items.findByBranchIdAndSkuIgnoreCase(branch,sku);
        if(existing.isPresent()) return existing.get();
        var row=rows(upload,"Items").stream().filter(r->sku.equalsIgnoreCase(value(r,"item_sku"))).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown parent item"));
        Item planned=new Item();planned.setSku(sku);planned.setBranchId(branch);planned.setIsWeighed(value(row,"is_weighed").equalsIgnoreCase("true"));return planned;
    }
    private void validateBarcode(Long branch,Map<String,String> r,String key,Long itemId,Long unitId,Set<String> seen) {
        String barcode=value(r,key);if(barcode.isEmpty())return;
        required(r,key,100);
        if(!seen.add("barcode:"+barcode.toLowerCase(Locale.ROOT)))fail("Duplicate barcode inside workbook");
        units.findByBranchIdAndBarcode(branch,barcode).ifPresent(u->{
            if(unitId!=null ? !unitId.equals(u.getUnitId()) : !(key.equals("base_barcode")&&Objects.equals(itemId,u.getItemId())&&Boolean.TRUE.equals(u.getIsBaseUnit()))) fail("Barcode already in use");
        });
    }
    private String signature(Map<String,String> r) {
        SortedMap<String,String> parts=new TreeMap<>();
        parts.put(required(r,"attribute_name",80).toLowerCase(Locale.ROOT),required(r,"attribute_value",120).toLowerCase(Locale.ROOT));
        for(int index=2;index<=20;index++) {
            String name="attribute_"+index+"_name",value="attribute_"+index+"_value";
            if(!value(r,name).isEmpty()||!value(r,value).isEmpty()) {
                String key=required(r,name,80).toLowerCase(Locale.ROOT);
                if(parts.putIfAbsent(key,required(r,value,120).toLowerCase(Locale.ROOT))!=null)fail("Duplicate attribute name");
            }
        }
        String result=parts.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("|"));
        if(result.length()>512)fail("Variant attributes exceed 512 characters");return result;
    }
    private boolean hasAttributes(Map<String,String> r) {return r.entrySet().stream().anyMatch(e->e.getKey().startsWith("attribute")&&!value(r,e.getKey()).isEmpty());}
    private void verifyExport(Long branch,Map<String,String> row,Map<Long,Map<String,Map<String,String>>> cache) {
        Long id=Long.valueOf(required(row,"snapshot_id",30));
        if(!cache.containsKey(id)) {
            InventoryImportJob snapshot=job(branch,id);
            if(!"SNAPSHOT".equals(snapshot.getMode()))fail("Invalid count export reference");
            try {
                List<Map<String,String>> originals=mapper.readValue(snapshot.getPayload(),new TypeReference<>(){});
                Map<String,Map<String,String>> indexed=new HashMap<>();originals.forEach(r->indexed.put(r.get("batch_barcode"),r));cache.put(id,indexed);
            } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException(e);}
        }
            var original=cache.get(id).get(row.get("batch_barcode"));
            if(original==null)fail("Batch was not included in export");
            for(String key:List.of("item_sku","variant_sku","base_unit","snapshot_version","snapshot_available","snapshot_damaged","snapshot_expired"))
                if(key.startsWith("snapshot_")&&!key.equals("snapshot_version") ? decimal(original,key,true).compareTo(decimal(row,key,true))!=0 : !value(original,key).equals(value(row,key)))fail("Protected export field changed: "+key);
    }
    private Long masterUnit(Long branch,String name) {
        return masterUnits.findByBranchId(branch).stream().filter(u->u.getName().equalsIgnoreCase(name)).findFirst().orElseGet(()->masterUnits.save(new UnitMaster(null,branch,name,true,LocalDateTime.now(),LocalDateTime.now()))).getUnitId();
    }

    @Transactional(readOnly=true)
    public Map<String,List<Map<String,String>>> exportCatalog(Long branch) {
        Map<String,List<Map<String,String>>> result=new LinkedHashMap<>();for(String sheet:List.of("Items","Units","Variants"))result.put(sheet,new ArrayList<>());
        for(var item:items.findByBranchId(branch)) {
            var base=units.findByItemIdAndIsBaseUnitTrue(item.getItemId()).orElse(null);if(base==null)continue;
            Map<String,String> r=new LinkedHashMap<>();r.put("action","UPDATE");r.put("item_sku",item.getSku());r.put("item_name",item.getName());r.put("base_unit",base.getUnitName());r.put("base_barcode",Objects.toString(base.getBarcode(),""));r.put("base_selling_price",base.getDefaultSellingPrice().toPlainString());r.put("is_active",item.getIsActive().toString());r.put("is_weighed",item.getIsWeighed().toString());r.put("minimum_stock",Objects.toString(item.getMinStock(),""));r.put("maximum_stock",Objects.toString(item.getMaxStock(),""));r.put("scale_barcode_prefix",Objects.toString(item.getScaleBarcodePrefix(),""));
            r.put("category",item.getCategoryId()==null?"":categories.findById(item.getCategoryId()).map(Category::getName).orElse(""));r.put("brand",item.getBrandId()==null?"":brands.findById(item.getBrandId()).map(Brand::getName).orElse(""));r.put("scale_item_code",scaleMappings.findByItemIdAndIsActiveTrue(item.getItemId()).map(ScaleItemMapping::getScaleItemCode).orElse(""));result.get("Items").add(r);
            for(var unit:units.findByItemId(item.getItemId())) if(!Boolean.TRUE.equals(unit.getIsBaseUnit())) {
                Map<String,String> u=new LinkedHashMap<>();u.put("item_sku",item.getSku());u.put("unit_name",unit.getUnitName());u.put("multiplier_to_base",unit.getMultiplierToBase().toPlainString());u.put("barcode",Objects.toString(unit.getBarcode(),""));u.put("selling_price",unit.getDefaultSellingPrice().toPlainString());u.put("is_active",unit.getIsActive().toString());result.get("Units").add(u);
            }
            for(var v:variants.findByItemIdOrderByVariantIdAsc(item.getItemId())) {
                Map<String,String> row=new LinkedHashMap<>();row.put("item_sku",item.getSku());row.put("variant_sku",Objects.toString(v.getSku(),v.getVariantSku()));row.put("selling_price",Objects.toString(v.getDefaultSellingPrice(),"0"));row.put("is_active",v.getIsActive().toString());
                var attrs=attributes.findByVariantIdOrderByAttributeNameAsc(v.getVariantId());if(attrs.size()>20)fail("Variant has more than 20 attributes: "+v.getVariantSku());
                for(int n=0;n<attrs.size();n++) {String prefix=n==0?"attribute":"attribute_"+(n+1);row.put(prefix+"_name",attrs.get(n).getAttributeName());row.put(prefix+"_value",attrs.get(n).getAttributeValue());}result.get("Variants").add(row);
            }
        }
        return result;
    }
    private static BigDecimal available(StockBatch b) {return b.getAvailableQty()==null?nvl(b.getQtyRemaining()):b.getAvailableQty();}
    static BigDecimal money(Map<String,String> row,String key) {BigDecimal n=decimal(row,key,true);if(n.scale()>2||n.precision()-n.scale()>8)fail(key+" must fit 8 digits and 2 decimal places");return n;}
    private ItemUnit resolveUnit(Item i,String name) { return units.findByItemId(i.getItemId()).stream().filter(u->u.getUnitName().equalsIgnoreCase(name)&&Boolean.TRUE.equals(u.getIsActive())).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown or inactive unit")); }
    private Item item(Long branch,String sku) { Item i=items.findByBranchIdAndSkuIgnoreCase(branch,sku).orElseThrow(()->new IllegalArgumentException("Unknown item SKU; import catalog first")); if(!Boolean.TRUE.equals(i.getIsActive())) fail("Item is inactive"); return i; }
    private StockBatch batch(Long branch,String barcode) { return batches.findByBranchIdAndInternalBatchBarcode(branch,barcode).orElseThrow(()->new IllegalArgumentException("Unknown batch")); }
    private InventoryImportJob job(Long branch,Long id) { return jobs.findById(id).filter(j->j.getBranchId().equals(branch)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private View view(InventoryImportJob j) { try {
        Map<String,List<Map<String,String>>> preview=new LinkedHashMap<>();
        if(!"SNAPSHOT".equals(j.getMode())) read(j.getPayload(),Upload.class).sheets().forEach((sheet,rows)->preview.put(sheet,rows.stream().limit(25).map(row->{
            Map<String,String> display=new LinkedHashMap<>(row);display.remove("_excel_row");display.remove("snapshot_id");display.remove("snapshot_version");
            if(sheet.equals("Stock_Count"))try {
                for(String condition:List.of("available","damaged","expired"))display.put("variance_"+condition,decimal(row,"counted_"+condition,true).subtract(decimal(row,"snapshot_"+condition,true)).toPlainString());
                var batch=batch(j.getBranchId(),row.get("batch_barcode"));
                if(batch.getCostPrice()!=null)display.put("estimated_available_value_change",new BigDecimal(display.get("variance_available")).multiply(batch.getCostPrice()).divide(units.findById(batch.getUnitId()).orElseThrow().getMultiplierToBase(),2,java.math.RoundingMode.HALF_UP).toPlainString());
            }catch(RuntimeException ignored){/* Invalid rows are reported separately, without inventing variances. */}
            return display;
        }).toList()));
        return new View(j.getId(),j.getFileName(),j.getMode(),j.getStatus(),j.getUploadedBy(),j.getCreatedAt(),j.getRowCount(),mapper.readValue(j.getErrors(),new TypeReference<List<Issue>>(){}),preview);
    } catch(Exception e){throw new IllegalStateException(e);} }
    private List<Map<String,String>> rows(Upload u,String sheet) { return u.sheets().getOrDefault(sheet,List.of()); }
    private Set<String> postedOpeningReferences(Long branch) {
        Set<String> references=new HashSet<>();
        for(String mode:List.of("OPENING","INITIAL"))for(var previous:jobs.findByBranchIdAndModeAndStatus(branch,mode,"POSTED")) {
            var data=read(previous.getPayload(),Upload.class);
            for(var row:rows(data,mode.equals("INITIAL")?"Initial_Stock":"Opening_Stock"))if(!mode.equals("INITIAL")||decimal(row,"quantity",true).signum()>0) {
                // Keep raw references for compatibility with advanced OPENING jobs and
                // historical imports, plus the item/variant-scoped INITIAL posting key.
                references.add(value(row,"row_reference").toLowerCase(Locale.ROOT));
                if(mode.equals("INITIAL"))references.add(initialStock.openingReference(row).toLowerCase(Locale.ROOT));
            }
        }
        return references;
    }
    private String json(Object o) { try{return mapper.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException("Invalid workbook",e);} }
    private <T>T read(String s,Class<T> type) { try{return mapper.readValue(s,type);}catch(Exception e){throw new IllegalStateException(e);} }
    static String value(Map<String,String> r,String key) { return Objects.toString(r.get(key),"").trim(); }
    static String required(Map<String,String> r,String key,int max) {String s=value(r,key); if(s.isEmpty()||s.length()>max) fail(key+" is required (maximum "+max+" characters)"); return s;}
    static BigDecimal decimal(Map<String,String> r,String key,boolean positive) {try {BigDecimal n=new BigDecimal(required(r,key,30));if(n.scale()>4||n.precision()-n.scale()>10||(positive&&n.signum()<0)) fail(key+" must be nonnegative, at most 10 integer digits and 4 decimal places");return n;}catch(NumberFormatException e){throw new IllegalArgumentException(key+" must be a number");}}
    static boolean bool(Map<String,String> r,String key) {String s=required(r,key,5);if(!s.equalsIgnoreCase("true")&&!s.equalsIgnoreCase("false")) fail(key+" must be TRUE or FALSE");return Boolean.parseBoolean(s);}
    private static BigDecimal nvl(BigDecimal n) {return n==null?BigDecimal.ZERO:n;}
    private static void fail(String message) {throw new IllegalArgumentException(message);}
}
