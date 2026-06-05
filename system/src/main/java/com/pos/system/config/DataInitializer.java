package com.pos.system.config;

import com.pos.system.model.auth.Authorization;
import com.pos.system.model.auth.AuthorizationStatus;
import com.pos.system.model.auth.AuthorizationType;
import com.pos.system.model.auth.User;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.stock.Stock;
import com.pos.system.repository.AuthorizationRepository;
import com.pos.system.repository.ItemUnitRepository;
import com.pos.system.repository.StockRepository;
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
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        removeOldStockQuantityColumns();
        repairRolePermissionUniqueIndex();
        migrateRolePermissionAuthorityCodes();

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
}
