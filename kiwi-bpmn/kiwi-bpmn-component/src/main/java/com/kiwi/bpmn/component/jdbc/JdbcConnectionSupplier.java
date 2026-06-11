package com.kiwi.bpmn.component.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 按已保存连接配置 id 打开 JDBC 连接；由 kiwi-admin 在运行时提供实现。
 */
public interface JdbcConnectionSupplier {

    Connection openConnection(String connectionId) throws SQLException;
}
