package com.kiwi.bpmn.component.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kiwi.common.utils.JsonUtils;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC 单语句执行：queryOne / query / update。
 */
public class JdbcSqlSupport {

    private static final int DefaultMaxRows = 500;
    private static final int MaxMaxRows = 10000;

    public void validateSingleStatement(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("流程变量 sql 不能为空");
        }
        String trimmed = sql.trim();
        int idx = trimmed.indexOf(';');
        if (idx >= 0 && idx < trimmed.length() - 1) {
            String after = trimmed.substring(idx + 1).trim();
            if (!after.isEmpty()) {
                throw new IllegalArgumentException("不允许执行多条 SQL 语句");
            }
        }
    }

    public List<?> parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return List.of();
        }
        try {
            List<?> list = JsonUtils.readValue(paramsJson.trim(), new TypeReference<List<Object>>() {});
            return list != null ? list : List.of();
        } catch (Exception e) {
            throw new IllegalArgumentException("params 不是合法 JSON 数组: " + e.getMessage(), e);
        }
    }

    public int resolveMaxRows(Integer maxRows) {
        if (maxRows == null) {
            return DefaultMaxRows;
        }
        return Math.min(Math.max(maxRows, 1), MaxMaxRows);
    }

    public Object execute(
            Connection connection,
            String operation,
            String sql,
            List<?> params,
            int maxRows,
            Integer queryTimeoutSeconds) throws SQLException {
        validateSingleStatement(sql);
        String op = operation == null || operation.isBlank()
                ? "queryone"
                : operation.trim().toLowerCase(Locale.ROOT);
        return switch (op) {
            case "queryone" -> queryOne(connection, sql, params, queryTimeoutSeconds);
            case "query" -> query(connection, sql, params, maxRows, queryTimeoutSeconds);
            case "update" -> update(connection, sql, params, queryTimeoutSeconds);
            default -> throw new IllegalArgumentException(
                    "不支持的操作: " + operation + "，请使用 queryOne | query | update");
        };
    }

    private Object queryOne(
            Connection connection, String sql, List<?> params, Integer queryTimeoutSeconds) throws SQLException {
        try (PreparedStatement ps = prepare(connection, sql, params, queryTimeoutSeconds);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return rowToMap(rs);
        }
    }

    private List<Map<String, Object>> query(
            Connection connection,
            String sql,
            List<?> params,
            int maxRows,
            Integer queryTimeoutSeconds) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = prepare(connection, sql, params, queryTimeoutSeconds);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next() && rows.size() < maxRows) {
                rows.add(rowToMap(rs));
            }
        }
        return rows;
    }

    private int update(
            Connection connection, String sql, List<?> params, Integer queryTimeoutSeconds) throws SQLException {
        try (PreparedStatement ps = prepare(connection, sql, params, queryTimeoutSeconds)) {
            return ps.executeUpdate();
        }
    }

    private PreparedStatement prepare(
            Connection connection, String sql, List<?> params, Integer queryTimeoutSeconds) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql.trim());
        if (queryTimeoutSeconds != null && queryTimeoutSeconds > 0) {
            ps.setQueryTimeout(queryTimeoutSeconds);
        }
        bindParams(ps, params);
        return ps;
    }

    private void bindParams(PreparedStatement ps, List<?> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            int index = i + 1;
            if (value == null) {
                ps.setNull(index, Types.NULL);
            } else if (value instanceof Number number) {
                ps.setObject(index, number);
            } else if (value instanceof Boolean bool) {
                ps.setBoolean(index, bool);
            } else {
                ps.setString(index, value.toString());
            }
        }
    }

    private Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            row.put(label, normalizeCellValue(rs.getObject(i)));
        }
        return row;
    }

    /**
     * 将 JDBC 返回值转为 Camunda 流程变量易序列化的类型。
     */
    Object normalizeCellValue(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Blob blob) {
            try {
                long length = blob.length();
                if (length > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("BLOB 过大，无法写入流程变量");
                }
                return Base64.getEncoder().encodeToString(blob.getBytes(1, (int) length));
            } finally {
                blob.free();
            }
        }
        if (value instanceof Clob clob) {
            try {
                long length = clob.length();
                if (length > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("CLOB 过大，无法写入流程变量");
                }
                return clob.getSubString(1, (int) length);
            } finally {
                clob.free();
            }
        }
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        if (value instanceof TemporalAccessor temporal) {
            return temporal.toString();
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        return value.toString();
    }
}
