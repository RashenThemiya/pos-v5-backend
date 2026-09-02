package com.pos.system.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VariantSchemaMaintenance implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String database = metaData.getDatabaseProductName().toLowerCase();
            if (!database.contains("mysql") && !database.contains("mariadb")) {
                return;
            }
        }

        dropLegacyUniqueIndexes(
                "supplier_items",
                "branch_id,supplier_id,item_id,unit_id"
        );
        dropLegacyUniqueIndexes(
                "purchase_order_items",
                "po_id,item_id,unit_id"
        );
        dropLegacyUniqueIndexes(
                "stock",
                "branch_id,item_id"
        );
    }

    private void dropLegacyUniqueIndexes(String tableName, String columnList) {
        List<String> indexNames = jdbcTemplate.queryForList(
                """
                SELECT index_name
                FROM (
                    SELECT index_name,
                           GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_in_index
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                      AND non_unique = 0
                      AND index_name <> 'PRIMARY'
                    GROUP BY index_name
                ) indexes_by_columns
                WHERE columns_in_index = ?
                """,
                String.class,
                tableName,
                columnList
        );

        for (String indexName : indexNames) {
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP INDEX `" + indexName + "`");
            log.info("Dropped legacy unique index {} on {}", indexName, tableName);
        }
    }
}
