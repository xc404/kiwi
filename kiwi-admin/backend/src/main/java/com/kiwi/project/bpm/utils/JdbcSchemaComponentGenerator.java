package com.kiwi.project.bpm.utils;

import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.tools.codegen.entity.GenEnum;
import com.kiwi.project.tools.codegen.utils.JdbcTableReader;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 基于 JDBC 连接表结构，为每张表生成继承 {@code jdbcActivity} 父组件的 CRUD 子组件草稿。
 */
public final class JdbcSchemaComponentGenerator {

    /** {@link BpmComponent#getSource()} */
    public static final String ComponentSource = "dbschema";

    private static final String SourceKeyPrefix = "dbschema:v1|";

    private JdbcSchemaComponentGenerator() {
    }

    public static List<BpmComponent> buildComponents(
            Connection connection, String connectionId, List<String> tableNames, String jdbcParentId)
            throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("connection 不能为空");
        }
        if (StringUtils.isBlank(connectionId)) {
            throw new IllegalArgumentException("connectionId 不能为空");
        }
        if (tableNames == null || tableNames.isEmpty()) {
            throw new IllegalArgumentException("tables 不能为空");
        }
        if (StringUtils.isBlank(jdbcParentId)) {
            throw new IllegalArgumentException("jdbcParentId 不能为空");
        }

        GenEnum.DatabaseType dbType = GenEnum.DatabaseType.fromUrl(connection.getMetaData().getURL());
        List<TableSchema> schemas = new ArrayList<>();
        for (String tableName : tableNames) {
            if (StringUtils.isBlank(tableName)) {
                continue;
            }
            String trimmed = tableName.trim();
            TableSchema schema = readTableSchema(connection, trimmed, dbType);
            if (schema == null) {
                throw new IllegalArgumentException("表不存在或无法读取元数据: " + trimmed);
            }
            schemas.add(schema);
        }
        return buildFromSchemas(connectionId, schemas, jdbcParentId);
    }

    /** 基于已解析的表结构生成 CRUD 组件（供测试与复用）。 */
    public static List<BpmComponent> buildFromSchemas(
            String connectionId, List<TableSchema> schemas, String jdbcParentId) {
        List<BpmComponent> out = new ArrayList<>();
        for (TableSchema schema : schemas) {
            out.addAll(buildCrudForTable(schema, connectionId, jdbcParentId));
        }
        return out;
    }

    static TableSchema readTableSchema(Connection connection, String tableName, GenEnum.DatabaseType dbType)
            throws SQLException {
        var codeGenVo = JdbcTableReader.readTable(connection, tableName);
        if (codeGenVo == null || codeGenVo.getGenEntity() == null) {
            return null;
        }
        List<ColumnMeta> columns = new ArrayList<>();
        if (codeGenVo.getFields() != null) {
            for (var f : codeGenVo.getFields()) {
                if (f == null || StringUtils.isBlank(f.getColumnName())) {
                    continue;
                }
                columns.add(new ColumnMeta(f.getColumnName(), StringUtils.trimToEmpty(f.getColumnComment())));
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("表无列定义: " + tableName);
        }
        List<String> pkColumns = readPrimaryKeyColumns(connection, tableName);
        boolean primaryKeyDetected = !pkColumns.isEmpty();
        if (!primaryKeyDetected) {
            pkColumns = List.of(columns.getFirst().name());
        }
        String comment = codeGenVo.getGenEntity().getTableComment();
        return new TableSchema(
                tableName,
                StringUtils.trimToEmpty(comment),
                columns,
                pkColumns,
                primaryKeyDetected,
                dbType);
    }

    private static List<String> readPrimaryKeyColumns(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        List<String> pk = new ArrayList<>();
        try (ResultSet rs =
                meta.getPrimaryKeys(connection.getCatalog(), connection.getSchema(), tableName)) {
            while (rs.next()) {
                String col = rs.getString(JdbcTableReader.COLUMN_NAME);
                if (StringUtils.isNotBlank(col)) {
                    pk.add(col);
                }
            }
        }
        return pk;
    }

    private static List<BpmComponent> buildCrudForTable(
            TableSchema schema, String connectionId, String jdbcParentId) {
        List<BpmComponent> list = new ArrayList<>();
        list.add(buildOne(schema, connectionId, jdbcParentId, CrudOp.GetById));
        list.add(buildOne(schema, connectionId, jdbcParentId, CrudOp.SelectList));
        list.add(buildOne(schema, connectionId, jdbcParentId, CrudOp.Insert));
        list.add(buildOne(schema, connectionId, jdbcParentId, CrudOp.Update));
        list.add(buildOne(schema, connectionId, jdbcParentId, CrudOp.Delete));
        return list;
    }

    private static BpmComponent buildOne(
            TableSchema schema, String connectionId, String jdbcParentId, CrudOp op) {
        BpmComponent c = new BpmComponent();
        c.setParentId(jdbcParentId);
        c.setType(BpmComponent.Type.SpringBean);
        c.setKey(buildKey(schema.tableName(), op));
        c.setSource(ComponentSource);
        c.setSourceKey(buildSourceKey(connectionId, schema.tableName(), op));
        c.setName(buildName(schema, op));
        c.setDescription(buildDescription(schema, connectionId, op));
        c.setGroup(buildGroup(schema));
        c.setInputParameters(buildInputs(schema, connectionId, op));
        c.setOutputParameters(null);
        return c;
    }

    private static String buildKey(String tableName, CrudOp op) {
        return "dbschema_" + slug(tableName) + "_" + op.suffix;
    }

    static String buildSourceKey(String connectionId, String tableName, CrudOp op) {
        return SourceKeyPrefix + connectionId + "|" + tableName + "|" + op.suffix;
    }

    private static String buildName(TableSchema schema, CrudOp op) {
        String tableLabel =
                StringUtils.isNotBlank(schema.tableComment()) ? schema.tableComment() : schema.tableName();
        return tableLabel + " · " + op.label;
    }

    private static String buildGroup(TableSchema schema) {
        return "数据库/" + schema.tableName();
    }

    private static String buildDescription(TableSchema schema, String connectionId, CrudOp op) {
        StringBuilder sb = new StringBuilder();
        sb.append("由表结构自动生成（connectionId=").append(connectionId).append("）\n");
        sb.append("表: ").append(schema.tableName());
        if (StringUtils.isNotBlank(schema.tableComment())) {
            sb.append(" — ").append(schema.tableComment());
        }
        sb.append("\n主键: ").append(String.join(", ", schema.primaryKeyColumns()));
        sb.append("\n操作: ").append(op.label);
        if (!schema.primaryKeyDetected()) {
            sb.append("\n注意: 未检测到主键，已回退使用首列 ").append(schema.primaryKeyColumns().getFirst());
        }
        return sb.toString();
    }

    private static List<BpmComponentParameter> buildInputs(
            TableSchema schema, String connectionId, CrudOp op) {
        List<BpmComponentParameter> list = new ArrayList<>();
        Set<String> usedKeys = new LinkedHashSet<>();
        Map<String, String> colKeyByName = new LinkedHashMap<>();

        List<ColumnMeta> visibleCols = op.visibleColumns(schema);
        for (ColumnMeta col : visibleCols) {
            String key = uniquifyKey("col_" + slug(col.name()), usedKeys);
            colKeyByName.put(col.name(), key);
            BpmComponentParameter p = new BpmComponentParameter();
            p.setKey(key);
            p.setName(col.name());
            p.setDescription(
                    StringUtils.isNotBlank(col.comment())
                            ? col.comment()
                            : "列 " + col.name());
            p.setGroup("列");
            p.setImportant(op.isImportantColumn(schema, col));
            p.setRequired(op.isRequiredColumn(schema, col));
            list.add(p);
        }

        if (op == CrudOp.SelectList) {
            BpmComponentParameter maxRows = new BpmComponentParameter();
            maxRows.setKey("max_rows");
            maxRows.setName("query 条数上限");
            maxRows.setDescription("仅 query 有效，默认 500");
            maxRows.setGroup("JDBC");
            maxRows.setRequired(false);
            maxRows.setDefaultValue("500");
            list.add(maxRows);
        }

        list.add(hiddenJdbcField("connection_id", "连接", "JDBC 连接 id", connectionId));
        list.add(hiddenJdbcField("operation", "操作", "queryOne | query | update", op.operation));
        list.add(hiddenJdbcField("sql", "SQL", "预编译 SQL", op.buildSql(schema)));
        list.add(
                hiddenJdbcField(
                        "params",
                        "params(JSON)",
                        "PreparedStatement 参数 JSON 数组",
                        op.buildParamsTemplate(schema, visibleCols, colKeyByName)));
        return list;
    }

    private static BpmComponentParameter hiddenJdbcField(
            String key, String name, String description, String defaultValue) {
        BpmComponentParameter p = new BpmComponentParameter();
        p.setKey(key);
        p.setName(name);
        p.setDescription(description);
        p.setGroup("JDBC");
        p.setHidden(true);
        p.setRequired(false);
        p.setDefaultValue(defaultValue);
        return p;
    }

    private static String uniquifyKey(String base, Set<String> used) {
        String k = base;
        int n = 2;
        while (used.contains(k)) {
            k = base + "_" + n;
            n++;
        }
        used.add(k);
        return k;
    }

    private static String slug(String raw) {
        if (raw == null) {
            return "tbl";
        }
        String s =
                raw.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "_")
                        .replaceAll("^_+|_+$", "");
        return StringUtils.isBlank(s) ? "tbl" : s;
    }

    static String quoteIdentifier(String ident, GenEnum.DatabaseType dbType) {
        if (ident == null) {
            return "";
        }
        if (ident.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return ident;
        }
        char q = dbType == GenEnum.DatabaseType.MySQL ? '`' : '"';
        return q + ident.replace(String.valueOf(q), String.valueOf(q) + q) + q;
    }

    record ColumnMeta(String name, String comment) {}

    record TableSchema(
            String tableName,
            String tableComment,
            List<ColumnMeta> columns,
            List<String> primaryKeyColumns,
            boolean primaryKeyDetected,
            GenEnum.DatabaseType databaseType) {}

    enum CrudOp {
        GetById("get", "按主键查询", "queryOne") {
            @Override
            List<ColumnMeta> visibleColumns(TableSchema schema) {
                return schema.columns().stream()
                        .filter(c -> schema.primaryKeyColumns().contains(c.name()))
                        .toList();
            }

            @Override
            String buildSql(TableSchema schema) {
                String table = quoteIdentifier(schema.tableName(), schema.databaseType());
                String where =
                        schema.primaryKeyColumns().stream()
                                .map(c -> quoteIdentifier(c, schema.databaseType()) + " = ?")
                                .reduce((a, b) -> a + " AND " + b)
                                .orElse("1 = 0");
                return "SELECT * FROM " + table + " WHERE " + where;
            }
        },
        SelectList("list", "列表查询", "query") {
            @Override
            List<ColumnMeta> visibleColumns(TableSchema schema) {
                return java.util.List.of();
            }

            @Override
            String buildSql(TableSchema schema) {
                String table = quoteIdentifier(schema.tableName(), schema.databaseType());
                return "SELECT * FROM " + table;
            }

            @Override
            String buildParamsTemplate(List<ColumnMeta> cols, Map<String, String> colKeyByName) {
                return "[]";
            }
        },
        Insert("insert", "新增", "update") {
            @Override
            List<ColumnMeta> visibleColumns(TableSchema schema) {
                return schema.columns();
            }

            @Override
            String buildSql(TableSchema schema) {
                String table = quoteIdentifier(schema.tableName(), schema.databaseType());
                List<String> names =
                        schema.columns().stream().map(ColumnMeta::name).toList();
                String cols =
                        names.stream()
                                .map(n -> quoteIdentifier(n, schema.databaseType()))
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                String placeholders =
                        names.stream().map(n -> "?").reduce((a, b) -> a + ", " + b).orElse("");
                return "INSERT INTO " + table + " (" + cols + ") VALUES (" + placeholders + ")";
            }
        },
        Update("update", "按主键更新", "update") {
            @Override
            List<ColumnMeta> visibleColumns(TableSchema schema) {
                Set<String> pk = new LinkedHashSet<>(schema.primaryKeyColumns());
                List<ColumnMeta> out = new ArrayList<>();
                for (ColumnMeta c : schema.columns()) {
                    if (!pk.contains(c.name())) {
                        out.add(c);
                    }
                }
                for (String pkName : schema.primaryKeyColumns()) {
                    schema.columns().stream()
                            .filter(c -> pkName.equals(c.name()))
                            .findFirst()
                            .ifPresent(out::add);
                }
                return out;
            }

            @Override
            boolean isImportantColumn(TableSchema schema, ColumnMeta col) {
                return true;
            }

            @Override
            boolean isRequiredColumn(TableSchema schema, ColumnMeta col) {
                return schema.primaryKeyColumns().contains(col.name());
            }

            @Override
            String buildSql(TableSchema schema) {
                String table = quoteIdentifier(schema.tableName(), schema.databaseType());
                Set<String> pk = new LinkedHashSet<>(schema.primaryKeyColumns());
                List<String> setParts = new ArrayList<>();
                for (ColumnMeta c : schema.columns()) {
                    if (!pk.contains(c.name())) {
                        setParts.add(quoteIdentifier(c.name(), schema.databaseType()) + " = ?");
                    }
                }
                String setClause = String.join(", ", setParts);
                String where =
                        schema.primaryKeyColumns().stream()
                                .map(c -> quoteIdentifier(c, schema.databaseType()) + " = ?")
                                .reduce((a, b) -> a + " AND " + b)
                                .orElse("1 = 0");
                return "UPDATE " + table + " SET " + setClause + " WHERE " + where;
            }

            @Override
            String buildParamsTemplate(
                    TableSchema schema, List<ColumnMeta> visibleCols, Map<String, String> colKeyByName) {
                return buildParamsTemplate(visibleCols, colKeyByName);
            }
        },
        Delete("delete", "按主键删除", "update") {
            @Override
            List<ColumnMeta> visibleColumns(TableSchema schema) {
                return schema.columns().stream()
                        .filter(c -> schema.primaryKeyColumns().contains(c.name()))
                        .toList();
            }

            @Override
            String buildSql(TableSchema schema) {
                String table = quoteIdentifier(schema.tableName(), schema.databaseType());
                String where =
                        schema.primaryKeyColumns().stream()
                                .map(c -> quoteIdentifier(c, schema.databaseType()) + " = ?")
                                .reduce((a, b) -> a + " AND " + b)
                                .orElse("1 = 0");
                return "DELETE FROM " + table + " WHERE " + where;
            }
        };

        final String suffix;
        final String label;
        final String operation;

        CrudOp(String suffix, String label, String operation) {
            this.suffix = suffix;
            this.label = label;
            this.operation = operation;
        }

        List<ColumnMeta> visibleColumns(TableSchema schema) {
            return java.util.List.of();
        }

        String buildSql(TableSchema schema) {
            return "";
        }

        String buildParamsTemplate(List<ColumnMeta> cols, Map<String, String> colKeyByName) {
            List<String> parts = new ArrayList<>();
            for (ColumnMeta c : cols) {
                parts.add(juelRef(colKeyByName, c.name()));
            }
            return buildParamsArray(parts);
        }

        String buildParamsTemplate(
                TableSchema schema, List<ColumnMeta> visibleCols, Map<String, String> colKeyByName) {
            return buildParamsTemplate(visibleCols, colKeyByName);
        }

        boolean isImportantColumn(TableSchema schema, ColumnMeta col) {
            return schema.primaryKeyColumns().contains(col.name());
        }

        boolean isRequiredColumn(TableSchema schema, ColumnMeta col) {
            return schema.primaryKeyColumns().contains(col.name());
        }
    }

    private static String juelRef(Map<String, String> colKeyByName, String columnName) {
        String key = colKeyByName.getOrDefault(columnName, "col_" + slug(columnName));
        return "\"${" + key + "}\"";
    }

    private static String buildParamsArray(List<String> juelParts) {
        if (juelParts.isEmpty()) {
            return "[]";
        }
        return "[" + String.join(", ", juelParts) + "]";
    }
}
