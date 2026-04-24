package com.kiwi.project.tools.jdbc.connection.ctl;

import com.kiwi.project.tools.jdbc.connection.dao.ConnectionSettingsDao;
import com.kiwi.project.tools.jdbc.connection.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/tools")
@RequiredArgsConstructor
@Tag(name = "JDBC 工具", description = "连接与表元数据")
public class TableCtl {

    private final ConnectionSettingsDao connectionSettingsDao;
    private final ConnectionService connectionService;

    private Connection getConnection(String connectionId) throws SQLException {
        return this.connectionService.getConnection(connectionId);
    }

    @Operation(operationId = "jdbc_listTables", summary = "列出某 JDBC 连接下的数据库表名及注释")
    @GetMapping("connection/{connectionId}/tables")
    public List<TableInfo> getTables(@PathVariable String connectionId) {
        List<TableInfo> tables = new ArrayList<>();
        try (Connection connection = getConnection(connectionId)) {
            DatabaseMetaData metaData = connection.getMetaData();
            String schema = connection.getSchema();
            ResultSet resultSet = metaData.getTables(connection.getCatalog(), schema, "%", new String[]{"TABLE"});
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                String remarks = resultSet.getString("REMARKS");
                tables.add(new TableInfo(tableName, remarks));
            }
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return tables;
    }

    @Data
    @AllArgsConstructor
    public static class TableInfo {
        private String id;
        private String name;
        private String comment;

        public TableInfo(String name, String comment) {
            this.id = name;
            this.name = name;
            this.comment = comment;
        }
    }
}
