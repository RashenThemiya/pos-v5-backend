package com.pos.system.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogImportServiceTest {

    @Test
    void parsesQuotedCommasEscapedQuotesAndNewlines() {
        String csv = "name,category,description\r\n"
                + "\"Product, Large\",\"[\"\"Food\"\",\"\"Snacks\"\"]\",\"line one\nline two\"\r\n";

        List<List<String>> rows = CatalogImportService.parseCsv(csv);

        assertEquals(2, rows.size());
        assertEquals(List.of("name", "category", "description"), rows.get(0));
        assertEquals("Product, Large", rows.get(1).get(0));
        assertEquals("[\"Food\",\"Snacks\"]", rows.get(1).get(1));
        assertEquals("line one\nline two", rows.get(1).get(2));
    }
}
