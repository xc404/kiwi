package com.kiwi.project.bpm.utils;

import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.tools.codegen.entity.GenEnum;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcSchemaComponentGeneratorTest {

    private static final String ConnectionId = "conn-demo";
    private static final String ParentId = "classpath_jdbcActivity";

    @Test
    void buildCrudComponents_forUserTable() {
        JdbcSchemaComponentGenerator.TableSchema schema =
                new JdbcSchemaComponentGenerator.TableSchema(
                        "sys_user",
                        "用户",
                        List.of(
                                new JdbcSchemaComponentGenerator.ColumnMeta("id", "主键"),
                                new JdbcSchemaComponentGenerator.ColumnMeta("name", "姓名"),
                                new JdbcSchemaComponentGenerator.ColumnMeta("email", "邮箱")),
                        List.of("id"),
                        true,
                        GenEnum.DatabaseType.MySQL);

        List<BpmComponent> components =
                JdbcSchemaComponentGenerator.buildFromSchemas(ConnectionId, List.of(schema), ParentId);

        assertEquals(5, components.size());
        assertEquals("dbschema_sys_user_get", components.get(0).getKey());
        assertEquals(
                JdbcSchemaComponentGenerator.buildSourceKey(
                        ConnectionId, "sys_user", JdbcSchemaComponentGenerator.CrudOp.GetById),
                components.get(0).getSourceKey());

        BpmComponent getById = components.get(0);
        Map<String, BpmComponentParameter> inputs = index(getById.getInputParameters());
        assertEquals(ConnectionId, inputs.get("connection_id").getDefaultValue());
        assertEquals("queryOne", inputs.get("operation").getDefaultValue());
        assertEquals("SELECT * FROM sys_user WHERE id = ?", inputs.get("sql").getDefaultValue());
        assertEquals("[\"${col_id}\"]", inputs.get("params").getDefaultValue());

        BpmComponent listOp = components.get(1);
        assertEquals("query", index(listOp.getInputParameters()).get("operation").getDefaultValue());

        BpmComponent insert = components.get(2);
        assertEquals(
                "INSERT INTO sys_user (id, name, email) VALUES (?, ?, ?)",
                index(insert.getInputParameters()).get("sql").getDefaultValue());

        BpmComponent update = components.get(3);
        assertEquals(
                "UPDATE sys_user SET name = ?, email = ? WHERE id = ?",
                index(update.getInputParameters()).get("sql").getDefaultValue());
        assertEquals(
                "[\"${col_name}\", \"${col_email}\", \"${col_id}\"]",
                index(update.getInputParameters()).get("params").getDefaultValue());
    }

    @Test
    void quoteIdentifier_escapesSpecialNames() {
        assertEquals("plain_col", JdbcSchemaComponentGenerator.quoteIdentifier("plain_col", GenEnum.DatabaseType.MySQL));
        assertEquals(
                "`user id`",
                JdbcSchemaComponentGenerator.quoteIdentifier("user id", GenEnum.DatabaseType.MySQL));
        assertEquals(
                "\"user-name\"",
                JdbcSchemaComponentGenerator.quoteIdentifier("user-name", GenEnum.DatabaseType.PostgreSQL));
    }

    private static Map<String, BpmComponentParameter> index(List<BpmComponentParameter> params) {
        return params.stream().collect(Collectors.toMap(BpmComponentParameter::getKey, Function.identity()));
    }
}
