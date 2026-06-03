package com.kiwi.bpmn.component.jdbc;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;

/**
 * 使用 {@link JdbcConnectionSupplier} 对已保存 JDBC 连接执行 queryOne / query / update。
 */
@ConditionalOnBean(JdbcConnectionSupplier.class)
@Component("jdbcActivity")
@ComponentDescription(
        name = "JDBC/SQL",
        group = "数据",
        version = "1.0",
        description = "对已保存 JDBC 连接执行 queryOne / query / update；params 为 JSON 数组绑定 PreparedStatement",
        inputs = {
                @ComponentParameter(
                        key = "connection_id",
                        name = "连接",
                        description = "工具模块中已保存的 JDBC 连接 id",
                        required = true,
                        dictKey = "jdbc-connections"),
                @ComponentParameter(
                        key = "operation",
                        name = "操作",
                        description = "queryOne | query | update，默认 queryOne",
                        schema = @Schema(defaultValue = "queryOne")),
                @ComponentParameter(
                        key = "sql",
                        name = "SQL",
                        description = "单条 SQL 语句",
                        required = true),
                @ComponentParameter(
                        key = "params",
                        name = "params(JSON)",
                        description = "可选，PreparedStatement  positional 参数 JSON 数组"),
                @ComponentParameter(
                        key = "max_rows",
                        name = "query 条数上限",
                        description = "仅 query 有效，默认 500，最大 10000",
                        schema = @Schema(defaultValue = "500")),
                @ComponentParameter(
                        key = "query_timeout_seconds",
                        name = "查询超时(秒)",
                        description = "可选 Statement 超时")
        },
        outputs = {
                @ComponentParameter(
                        key = "result",
                        name = "结果变量名",
                        description = "queryOne 为 Map；query 为 List<Map>；update 为影响行数",
                        schema = @Schema(defaultValue = "result"))
        })
public class JdbcActivity extends AbstractBpmnActivityBehavior {

    private final JdbcConnectionSupplier connectionSupplier;
    private final JdbcSqlSupport sqlSupport;

    @Autowired
    public JdbcActivity(JdbcConnectionSupplier connectionSupplier) {
        this.connectionSupplier = connectionSupplier;
        this.sqlSupport = new JdbcSqlSupport();
    }

    JdbcActivity(JdbcConnectionSupplier connectionSupplier, JdbcSqlSupport sqlSupport) {
        this.connectionSupplier = connectionSupplier;
        this.sqlSupport = sqlSupport;
    }

    @Override
    public void execute(ActivityExecution execution) throws Exception {
        String connectionId = ExecutionUtils.getStringInputVariable(execution, "connection_id")
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("流程变量 connection_id（JDBC 连接 id）不能为空"));

        String sql = ExecutionUtils.getStringInputVariable(execution, "sql")
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("流程变量 sql 不能为空"));

        String operation = ExecutionUtils.getStringInputVariable(execution, "operation")
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse("queryOne");

        String paramsJson = ExecutionUtils.getStringInputVariable(execution, "params").orElse(null);
        List<?> params = sqlSupport.parseParams(paramsJson);

        int maxRows = ExecutionUtils.getIntInputVariable(execution, "max_rows")
                .map(sqlSupport::resolveMaxRows)
                .orElse(sqlSupport.resolveMaxRows(null));

        Integer queryTimeout = ExecutionUtils.getIntInputVariable(execution, "query_timeout_seconds")
                .orElse(null);

        String resultVar = ExecutionUtils.getOutputVariableName(execution, "result");

        try (Connection connection = connectionSupplier.openConnection(connectionId)) {
            Object out = sqlSupport.execute(connection, operation, sql, params, maxRows, queryTimeout);
            if (resultVar != null && !resultVar.isBlank()) {
                execution.setVariable(resultVar, out);
            }
        }

        super.leave(execution);
    }
}
