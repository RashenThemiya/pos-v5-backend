package com.pos.system.config;

import com.pos.system.model.auth.Authorization;
import com.pos.system.model.auth.AuthorizationStatus;
import com.pos.system.model.auth.AuthorizationType;
import com.pos.system.model.auth.User;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.UnitMaster;
import com.pos.system.model.stock.Stock;
import com.pos.system.repository.AuthorizationRepository;
import com.pos.system.repository.ItemUnitRepository;
import com.pos.system.repository.StockRepository;
import com.pos.system.repository.UnitMasterRepository;
import com.pos.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AuthorizationRepository authorizationRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final UnitMasterRepository unitMasterRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        removeOldStockQuantityColumns();
        repairRolePermissionUniqueIndex();
        repairPurchaseOrderItemUniqueIndex();
        migrateRolePermissionAuthorityCodes();
        migrateItemUnitsToMasterUnits();

        // create default SUPERADMIN only when no auth records exist
        if (authorizationRepository.count() == 0) {
            Authorization superAdminAuth = new Authorization();
            superAdminAuth.setUsername("superadmin");
            superAdminAuth.setEmail("superadmin@example.com");
            superAdminAuth.setPasswordHash(passwordEncoder.encode("superadmin123"));
            superAdminAuth.setType(AuthorizationType.SUPERADMIN);
            superAdminAuth.setStatus(AuthorizationStatus.ACTIVE);

            Authorization saved = authorizationRepository.save(superAdminAuth);

            User superAdminUser = new User();
            superAdminUser.setAuthId(saved.getAuthId());
            superAdminUser.setBranchId(null); // SUPERADMIN has no branch
            superAdminUser.setFullName("Default Super Administrator");
            superAdminUser.setPhone(null);
            superAdminUser.setIsActive(true);
            userRepository.save(superAdminUser);

            System.out.println("Default SUPERADMIN created: superadmin / superadmin123");
        }

        // Migrate existing stocks to set unitId if null
        migrateStockUnitIds();
    }

    private void removeOldStockQuantityColumns() {
        dropColumnIfExists("stock", "available_qty");
        dropColumnIfExists("stock", "damaged_qty");
        dropColumnIfExists("stock", "expired_qty");
    }

    private void repairRolePermissionUniqueIndex() {
        if (!tableExists("role_permissions")
                || !columnExists("role_permissions", "role_id")
                || !columnExists("role_permissions", "authority_code")) {
            return;
        }

        List<String> roleOnlyUniqueIndexes = jdbcTemplate.queryForList(
                """
                SELECT INDEX_NAME
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'role_permissions'
                  AND NON_UNIQUE = 0
                  AND INDEX_NAME <> 'PRIMARY'
                GROUP BY INDEX_NAME
                HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'role_id'
                """,
                String.class
        );

        for (String indexName : roleOnlyUniqueIndexes) {
            jdbcTemplate.execute("ALTER TABLE role_permissions DROP INDEX " + indexName);
            System.out.println("Removed wrong unique index on role_permissions.role_id: " + indexName);
        }

        if (!uniqueIndexExists("role_permissions", "role_id,authority_code")) {
            jdbcTemplate.execute(
                    """
                    ALTER TABLE role_permissions
                    ADD CONSTRAINT uk_role_permission_role_authority
                    UNIQUE (role_id, authority_code)
                    """
            );
            System.out.println("Added unique index on role_permissions(role_id, authority_code)");
        }
    }

    private void repairPurchaseOrderItemUniqueIndex() {
        if (!tableExists("purchase_order_items")
                || !columnExists("purchase_order_items", "po_id")
                || !columnExists("purchase_order_items", "item_id")
                || !columnExists("purchase_order_items", "variant_id")
                || !columnExists("purchase_order_items", "unit_id")) {
            return;
        }

        List<String> legacyUniqueIndexes = jdbcTemplate.queryForList(
                """
                SELECT INDEX_NAME
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'purchase_order_items'
                  AND NON_UNIQUE = 0
                  AND INDEX_NAME <> 'PRIMARY'
                GROUP BY INDEX_NAME
                HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) IN (
                    'po_id,item_id',
                    'po_id,item_id,unit_id'
                )
                """,
                String.class
        );

        for (String indexName : legacyUniqueIndexes) {
            jdbcTemplate.execute("ALTER TABLE purchase_order_items DROP INDEX " + quoteIdentifier(indexName));
            System.out.println("Removed legacy unique index on purchase_order_items without variant_id: " + indexName);
        }

        if (!uniqueIndexExists("purchase_order_items", "po_id,item_id,variant_id,unit_id")) {
            jdbcTemplate.execute(
                    """
                    ALTER TABLE purchase_order_items
                    ADD CONSTRAINT uk_purchase_order_item_variant_unit
                    UNIQUE (po_id, item_id, variant_id, unit_id)
                    """
            );
            System.out.println("Added unique index on purchase_order_items(po_id, item_id, variant_id, unit_id)");
        }
    }

    private void migrateRolePermissionAuthorityCodes() {
        if (!columnExists("role_permissions", "permission_id")
                || !columnExists("role_permissions", "authority_code")
                || !tableExists("permissions")) {
            return;
        }

        jdbcTemplate.execute(
                """
                UPDATE role_permissions rp
                JOIN permissions p ON p.permission_id = rp.permission_id
                SET rp.authority_code = p.code
                WHERE rp.authority_code IS NULL
                """
        );

        dropColumnIfExists("role_permissions", "permission_id");
    }

    private void dropColumnIfExists(String tableName, String columnName) {
        if (columnExists(tableName, columnName)) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
            System.out.println("Removed old column: " + tableName + "." + columnName);
        }
    }

    private boolean tableExists(String tableName) {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """,
                Integer.class,
                tableName
        );

        return tableCount != null && tableCount > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer columnCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """,
                Integer.class,
                tableName,
                columnName
        );

        return columnCount != null && columnCount > 0;
    }

    private boolean uniqueIndexExists(String tableName, String orderedColumns) {
        Integer indexCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT INDEX_NAME
                    FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND NON_UNIQUE = 0
                      AND INDEX_NAME <> 'PRIMARY'
                    GROUP BY INDEX_NAME
                    HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = ?
                ) indexes_by_columns
                """,
                Integer.class,
                tableName,
                orderedColumns
        );

        return indexCount != null && indexCount > 0;
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private void migrateStockUnitIds() {
        List<Stock> stocksWithoutUnitId = stockRepository.findAll().stream()
                .filter(stock -> stock.getUnitId() == null)
                .collect(Collectors.toList());

        if (!stocksWithoutUnitId.isEmpty()) {
            System.out.println("Migrating " + stocksWithoutUnitId.size() + " stock records to set unitId...");

            for (Stock stock : stocksWithoutUnitId) {
                ItemUnit baseUnit = itemUnitRepository.findByItemIdAndIsBaseUnitTrue(stock.getItemId())
                        .orElseThrow(() -> new RuntimeException("Base unit not found for item: " + stock.getItemId()));
                stock.setUnitId(baseUnit.getUnitId());
                stockRepository.save(stock);
            }

            System.out.println("Stock migration completed.");
        }
    }

    private void migrateItemUnitsToMasterUnits() {
        if (!tableExists("item_units") || !columnExists("item_units", "master_unit_id")) {
            return;
        }

        List<ItemUnit> itemUnits = itemUnitRepository.findAll();
        int migrated = 0;

        for (ItemUnit itemUnit : itemUnits) {
            if (itemUnit.getBranchId() == null || itemUnit.getUnitName() == null || itemUnit.getUnitName().trim().isEmpty()) {
                continue;
            }

            String normalizedName = itemUnit.getUnitName().trim().toUpperCase();
            UnitMaster masterUnit = unitMasterRepository.findByBranchId(itemUnit.getBranchId())
                    .stream()
                    .filter(unit -> normalizedName.equals(unit.getName()))
                    .findFirst()
                    .orElseGet(() -> {
                        UnitMaster unit = new UnitMaster();
                        unit.setBranchId(itemUnit.getBranchId());
                        unit.setName(normalizedName);
                        unit.setIsActive(true);
                        unit.setCreatedAt(java.time.LocalDateTime.now());
                        unit.setUpdatedAt(java.time.LocalDateTime.now());
                        return unitMasterRepository.save(unit);
                    });

            boolean changed = false;
            if (itemUnit.getMasterUnitId() == null || !itemUnit.getMasterUnitId().equals(masterUnit.getUnitId())) {
                itemUnit.setMasterUnitId(masterUnit.getUnitId());
                changed = true;
            }

            if (!normalizedName.equals(itemUnit.getUnitName())) {
                itemUnit.setUnitName(normalizedName);
                changed = true;
            }

            if (changed) {
                itemUnit.setUpdatedAt(java.time.LocalDateTime.now());
                itemUnitRepository.save(itemUnit);
                migrated++;
            }
        }

        if (migrated > 0) {
            System.out.println("Migrated " + migrated + " item unit records to Unit Master.");
        }
    }
}
