package com.pos.system.service;

import com.pos.system.dto.item.CatalogImportResponse;
import com.pos.system.model.catalog.*;
import com.pos.system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
@RequiredArgsConstructor
public class CatalogImportService {

    private static final int MAX_ROWS = 10_000;
    private static final long MAX_CSV_BYTES = 25L * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 12L * 1024 * 1024;

    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final UnitMasterRepository unitMasterRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public CatalogImportResponse importZip(Long branchId, MultipartFile archive, boolean uploadImages) {
        if (!branchRepository.existsById(branchId)) {
            throw new RuntimeException("Branch not found: " + branchId);
        }
        if (archive == null || archive.isEmpty()) {
            throw new RuntimeException("Catalog ZIP file is required");
        }

        Path temporaryZip = null;
        try {
            temporaryZip = Files.createTempFile("pos-catalog-", ".zip");
            try (InputStream input = archive.getInputStream()) {
                Files.copy(input, temporaryZip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try (ZipFile zip = new ZipFile(temporaryZip.toFile())) {
                List<Map<String, String>> itemRows = readCsv(zip, "items.csv");
                List<Map<String, String>> variantRows = readCsv(zip, "item_variants.csv");
                List<Map<String, String>> unitRows = readCsv(zip, "item_units.csv");
                return importRows(branchId, zip, itemRows, variantRows, unitRows, uploadImages);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Cannot read catalog ZIP: " + ex.getMessage(), ex);
        } finally {
            if (temporaryZip != null) {
                try { Files.deleteIfExists(temporaryZip); } catch (IOException ignored) { }
            }
        }
    }

    private CatalogImportResponse importRows(Long branchId, ZipFile zip,
                                              List<Map<String, String>> itemRows,
                                              List<Map<String, String>> variantRows,
                                              List<Map<String, String>> unitRows,
                                              boolean uploadImages) throws IOException {
        MutableResult result = new MutableResult(branchId);
        if (itemRows.isEmpty() || variantRows.isEmpty()) {
            throw new RuntimeException("items.csv and item_variants.csv must contain data");
        }

        Map<String, Map<String, String>> itemsByName = itemRows.stream()
                .filter(row -> StringUtils.hasText(row.get("item_name")))
                .collect(Collectors.toMap(row -> row.get("item_name"), Function.identity(), (first, ignored) -> first,
                        LinkedHashMap::new));
        Map<String, Map<String, String>> unitsBySku = unitRows.stream()
                .filter(row -> StringUtils.hasText(row.get("sku")))
                .collect(Collectors.toMap(row -> row.get("sku"), Function.identity(), (first, ignored) -> first));

        Map<String, Category> categories = categoryRepository.findByBranchId(branchId).stream()
                .collect(Collectors.toMap(c -> c.getName().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        Map<String, Brand> brands = brandRepository.findByBranchId(branchId).stream()
                .collect(Collectors.toMap(b -> b.getName().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        Map<String, UnitMaster> masterUnits = unitMasterRepository.findByBranchId(branchId).stream()
                .collect(Collectors.toMap(u -> u.getName().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        for (Map<String, String> variantRow : variantRows) {
            String itemName = clean(variantRow.get("item_name"));
            String sku = limit(clean(variantRow.get("sku")), 100);
            if (!StringUtils.hasText(itemName) || !StringUtils.hasText(sku)) {
                result.skip("Skipped variant with missing item_name or sku");
                continue;
            }
            Map<String, String> itemRow = itemsByName.get(itemName);
            if (itemRow == null) {
                result.skip("No items.csv row for SKU " + sku);
                continue;
            }

            Brand brand = getOrCreateBrand(branchId, itemRow.get("brand"), brands, now, result);
            Category category = getOrCreateCategory(branchId, itemRow.get("category"), categories, now, result);
            Optional<Item> existingItem = itemRepository.findByBranchIdAndSku(branchId, sku);
            Item item = existingItem.orElseGet(Item::new);
            if (existingItem.isEmpty()) {
                item.setBranchId(branchId);
                item.setSku(sku);
                item.setCreatedAt(now);
                result.itemsCreated++;
            } else {
                result.itemsUpdated++;
            }
            item.setName(limit(itemName, 200));
            item.setBrandId(brand == null ? null : brand.getBrandId());
            item.setCategoryId(category == null ? null : category.getCategoryId());
            item.setIsWeighed(false);
            item.setIsActive(parseBoolean(itemRow.get("active"), true));
            item.setUpdatedAt(now);

            String imageFile = clean(variantRow.get("image_file"));
            if (uploadImages && !isStoredImage(item.getImage()) && StringUtils.hasText(imageFile)) {
                ZipEntry imageEntry = findEntry(zip, imageFile);
                if (imageEntry != null) {
                    byte[] image = readLimited(zip, imageEntry, MAX_IMAGE_BYTES);
                    item.setImage(fileStorageService.uploadCatalogImage(image, fileName(imageFile), imageType(imageFile)));
                    result.imagesUploaded++;
                } else {
                    result.warn("Image not found in ZIP: " + imageFile);
                }
            }
            if (!StringUtils.hasText(item.getImage())) {
                item.setImage(clean(variantRow.get("source_image_url")));
            }
            item = itemRepository.save(item);

            Map<String, String> unitRow = unitsBySku.get(sku);
            ensureUnit(branchId, item, variantRow, unitRow, masterUnits, now, result);
            ensureVariant(branchId, item, variantRow, now, result);
        }
        result.warn("Catalog descriptions and source URLs were not stored because the current item schema has no fields for them.");
        return result.response();
    }

    private Brand getOrCreateBrand(Long branchId, String rawName, Map<String, Brand> cache,
                                   LocalDateTime now, MutableResult result) {
        String name = stableName(clean(rawName), 100);
        if (!StringUtils.hasText(name)) return null;
        String key = name.toLowerCase(Locale.ROOT);
        Brand existing = cache.get(key);
        if (existing != null) return existing;
        Brand brand = new Brand(null, branchId, name, true, now);
        brand = brandRepository.save(brand);
        cache.put(key, brand);
        result.brandsCreated++;
        return brand;
    }

    private Category getOrCreateCategory(Long branchId, String rawPath, Map<String, Category> cache,
                                         LocalDateTime now, MutableResult result) {
        String name = stableCategoryName(clean(rawPath));
        if (!StringUtils.hasText(name)) return null;
        String key = name.toLowerCase(Locale.ROOT);
        Category existing = cache.get(key);
        if (existing != null) return existing;
        Category category = new Category(null, branchId, name, null, true, now);
        category = categoryRepository.save(category);
        cache.put(key, category);
        result.categoriesCreated++;
        return category;
    }

    private void ensureUnit(Long branchId, Item item, Map<String, String> variantRow,
                            Map<String, String> unitRow, Map<String, UnitMaster> masters,
                            LocalDateTime now, MutableResult result) {
        if (itemUnitRepository.findByItemIdAndIsBaseUnitTrue(item.getItemId()).isPresent()) return;
        String unitName = unitRow == null ? "pcs" : clean(unitRow.get("unit_symbol"));
        if (!StringUtils.hasText(unitName)) unitName = "pcs";
        unitName = limit(unitName, 30);
        String key = unitName.toLowerCase(Locale.ROOT);
        UnitMaster master = masters.get(key);
        if (master == null) {
            master = unitMasterRepository.save(new UnitMaster(null, branchId, unitName, true, now, now));
            masters.put(key, master);
        }
        ItemUnit unit = new ItemUnit();
        unit.setBranchId(branchId);
        unit.setItemId(item.getItemId());
        unit.setMasterUnitId(master.getUnitId());
        unit.setUnitName(unitName);
        unit.setMultiplierToBase(decimal(unitRow == null ? null : unitRow.get("conversion_qty"), BigDecimal.ONE));
        unit.setBarcode(firstBarcode(variantRow.get("barcode")));
        unit.setDefaultSellingPrice(BigDecimal.ZERO);
        unit.setIsBaseUnit(true);
        unit.setIsActive(unitRow == null || parseBoolean(unitRow.get("active"), true));
        unit.setCreatedAt(now);
        unit.setUpdatedAt(now);
        itemUnitRepository.save(unit);
        result.unitsCreated++;
    }

    private void ensureVariant(Long branchId, Item item, Map<String, String> row,
                               LocalDateTime now, MutableResult result) {
        String sku = limit(clean(row.get("sku")), 100);
        Optional<ItemVariant> existing = itemVariantRepository.findByBranchIdAndSku(branchId, sku);
        ItemVariant variant = existing.orElseGet(ItemVariant::new);
        if (existing.isEmpty()) {
            variant.setBranchId(branchId);
            variant.setItemId(item.getItemId());
            variant.setSku(sku);
            variant.setCreatedAt(now);
            result.variantsCreated++;
        } else {
            if (!variant.getItemId().equals(item.getItemId())) {
                throw new RuntimeException("Variant SKU already belongs to another item: " + sku);
            }
            result.variantsUpdated++;
        }
        variant.setVariantSku(sku);
        variant.setCombinationSignature(limit(Optional.ofNullable(clean(row.get("variant_name"))).orElse("Standard"), 512));
        variant.setDefaultSellingPrice(BigDecimal.ZERO);
        variant.setImage(item.getImage());
        variant.setIsActive(parseBoolean(row.get("active"), true));
        variant.setUpdatedAt(now);
        itemVariantRepository.save(variant);
    }

    private List<Map<String, String>> readCsv(ZipFile zip, String fileName) throws IOException {
        ZipEntry entry = findEntry(zip, fileName);
        if (entry == null) throw new RuntimeException("Missing " + fileName + " in catalog ZIP");
        String text = new String(readLimited(zip, entry, MAX_CSV_BYTES), StandardCharsets.UTF_8);
        List<List<String>> records = parseCsv(text);
        if (records.isEmpty()) return List.of();
        List<String> headers = records.get(0);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < records.size() && rows.size() < MAX_ROWS; i++) {
            List<String> record = records.get(i);
            Map<String, String> row = new HashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                row.put(headers.get(column).replace("\uFEFF", "").trim(), column < record.size() ? record.get(column) : "");
            }
            rows.add(row);
        }
        if (records.size() - 1 > MAX_ROWS) throw new RuntimeException(fileName + " exceeds " + MAX_ROWS + " rows");
        return rows;
    }

    static List<List<String>> parseCsv(String input) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < input.length() && input.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (c == '"') quoted = false;
                else field.append(c);
            } else if (c == '"' && field.length() == 0) quoted = true;
            else if (c == ',') { record.add(field.toString()); field.setLength(0); }
            else if (c == '\n') { record.add(stripCr(field.toString())); field.setLength(0); records.add(record); record = new ArrayList<>(); }
            else field.append(c);
        }
        if (field.length() > 0 || !record.isEmpty()) { record.add(stripCr(field.toString())); records.add(record); }
        return records;
    }

    private static ZipEntry findEntry(ZipFile zip, String requested) {
        String suffix = requested.replace('\\', '/').replaceFirst("^/+", "");
        return zip.stream().filter(e -> !e.isDirectory())
                .filter(e -> e.getName().replace('\\', '/').endsWith(suffix)).findFirst().orElse(null);
    }

    private static byte[] readLimited(ZipFile zip, ZipEntry entry, long maximum) throws IOException {
        if (entry.getSize() > maximum) throw new RuntimeException("ZIP entry is too large: " + entry.getName());
        try (InputStream input = zip.getInputStream(entry); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximum) throw new RuntimeException("ZIP entry is too large: " + entry.getName());
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String stableCategoryName(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String leaf = raw;
        int lastQuote = raw.lastIndexOf('"');
        if (lastQuote > 0) {
            int previous = raw.lastIndexOf('"', lastQuote - 1);
            if (previous >= 0) leaf = raw.substring(previous + 1, lastQuote);
        }
        String suffix = "-" + Integer.toUnsignedString(raw.hashCode(), 36);
        return limit(leaf, 100 - suffix.length()) + suffix;
    }

    private static String stableName(String value, int max) {
        if (!StringUtils.hasText(value) || value.length() <= max) return value;
        String suffix = "-" + Integer.toUnsignedString(value.hashCode(), 36);
        return limit(value, max - suffix.length()) + suffix;
    }
    private static String firstBarcode(String value) {
        String cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) return null;
        return limit(cleaned.split("\\s+")[0], 100);
    }
    private static boolean isStoredImage(String value) { return StringUtils.hasText(value) && value.contains("/item_images/catalog/"); }
    private static String imageType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
    private static String fileName(String path) { int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')); return slash < 0 ? path : path.substring(slash + 1); }
    private static String clean(String value) { return value == null ? null : value.trim(); }
    private static String limit(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }
    private static String stripCr(String value) { return value.endsWith("\r") ? value.substring(0, value.length() - 1) : value; }
    private static boolean parseBoolean(String value, boolean fallback) { return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : fallback; }
    private static BigDecimal decimal(String value, BigDecimal fallback) { try { return StringUtils.hasText(value) ? new BigDecimal(value.trim()) : fallback; } catch (NumberFormatException ex) { return fallback; } }

    private static class MutableResult {
        final Long branchId;
        int categoriesCreated, brandsCreated, itemsCreated, itemsUpdated, unitsCreated;
        int variantsCreated, variantsUpdated, imagesUploaded, skippedRows;
        final List<String> warnings = new ArrayList<>();
        MutableResult(Long branchId) { this.branchId = branchId; }
        void warn(String warning) { if (warnings.size() < 100) warnings.add(warning); }
        void skip(String warning) { skippedRows++; warn(warning); }
        CatalogImportResponse response() {
            return CatalogImportResponse.builder().branchId(branchId).categoriesCreated(categoriesCreated)
                    .brandsCreated(brandsCreated).itemsCreated(itemsCreated).itemsUpdated(itemsUpdated)
                    .unitsCreated(unitsCreated).variantsCreated(variantsCreated).variantsUpdated(variantsUpdated)
                    .imagesUploaded(imagesUploaded).skippedRows(skippedRows).warnings(warnings).build();
        }
    }
}
