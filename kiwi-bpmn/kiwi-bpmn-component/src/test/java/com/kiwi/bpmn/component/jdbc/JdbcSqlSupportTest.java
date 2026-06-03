package com.kiwi.bpmn.component.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcSqlSupportTest {

    private JdbcSqlSupport support;
    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        support = new JdbcSqlSupport();
        String db = "jdbc_sql_support_" + UUID.randomUUID();
        connection = DriverManager.getConnection("jdbc:h2:mem:" + db + ";DB_CLOSE_DELAY=-1");
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE demo (id INT PRIMARY KEY, name VARCHAR(50))");
            st.execute("INSERT INTO demo VALUES (1, 'alpha'), (2, 'beta')");
        }
    }

    @Test
    void validateSingleStatement_rejectsMultipleStatements() {
        assertThrows(IllegalArgumentException.class, () -> support.validateSingleStatement("SELECT 1; SELECT 2"));
    }

    @Test
    void queryOne_returnsFirstRow() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) support.execute(
                connection, "queryOne", "SELECT id, name FROM demo WHERE id = ?", List.of(1), 500, null);
        assertEquals(1, ((Number) row.get("ID")).intValue());
        assertEquals("alpha", row.get("NAME"));
    }

    @Test
    void queryOne_noRow_returnsNull() throws Exception {
        assertNull(support.execute(connection, "queryOne", "SELECT * FROM demo WHERE id = ?", List.of(99), 500, null));
    }

    @Test
    void query_returnsMultipleRows() throws Exception {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) support.execute(
                connection, "query", "SELECT id FROM demo ORDER BY id", List.of(), 500, null);
        assertEquals(2, rows.size());
    }

    @Test
    void update_returnsAffectedRows() throws Exception {
        int affected = (Integer) support.execute(
                connection,
                "update",
                "UPDATE demo SET name = ? WHERE id = ?",
                List.of("gamma", 1),
                500,
                null);
        assertEquals(1, affected);
    }

    @Test
    void parseParams_invalidJson() {
        assertThrows(IllegalArgumentException.class, () -> support.parseParams("not-json"));
    }

    @Test
    void execute_unknownOperation() {
        assertThrows(IllegalArgumentException.class, () -> support.execute(
                connection, "delete", "DELETE FROM demo", List.of(), 500, null));
    }

    @Test
    void queryOne_normalizesTimestampToIsoString() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE ts_demo (id INT PRIMARY KEY, created_at TIMESTAMP)");
            st.execute("INSERT INTO ts_demo VALUES (1, TIMESTAMP '2024-06-01 12:00:00')");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) support.execute(
                connection, "queryOne", "SELECT created_at FROM ts_demo WHERE id = 1", List.of(), 500, null);
        Object createdAt = row.get("CREATED_AT");
        assertInstanceOf(String.class, createdAt);
        assertEquals(Timestamp.valueOf("2024-06-01 12:00:00").toInstant().toString(), createdAt);
    }

    @Test
    void normalizeCellValue_bytesToBase64() throws Exception {
        assertEquals("YWI=", support.normalizeCellValue(new byte[] {'a', 'b'}));
    }
}
