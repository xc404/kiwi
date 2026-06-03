package com.kiwi.project.tools.jdbc.connection.service;

import com.kiwi.framework.security.PasswordService;
import com.kiwi.project.system.spi.Dict;
import com.kiwi.project.system.spi.DictProvider;
import com.kiwi.project.tools.jdbc.connection.dao.ConnectionSettingsDao;
import com.kiwi.project.tools.jdbc.connection.entity.ConnectionSettings;
import lombok.RequiredArgsConstructor;
import net.dreamlu.mica.core.utils.AesUtil;
import org.apache.commons.lang3.StringUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ConnectionService implements DictProvider
{
    private static final int ToolPoolMaxSize = 5;

    private final ConnectionSettingsDao connectionSettingsDao;
    private final PasswordService passwordService;
    private final ConcurrentHashMap<String, DataSource> dataSources = new ConcurrentHashMap<>();

    public ConnectionSettings decrypt(ConnectionSettings connectionSettings) {

        if( connectionSettings.getPassword() != null ) {
            String decodedPassword = AesUtil.decryptFormBase64ToString(connectionSettings.getPassword(), passwordService.getPasswordSecret());
            connectionSettings.setPassword(decodedPassword);
        }
        return connectionSettings;
    }

    public ConnectionSettings encrypt(ConnectionSettings connectionSettings) {
        if( StringUtils.isNotBlank(connectionSettings.getPassword()) ) {
            String encodedPassword = AesUtil.encryptToBase64(connectionSettings.getPassword(), passwordService.getPasswordSecret());
            connectionSettings.setPassword(encodedPassword);
        }
        return connectionSettings;
    }

    /**
     * 从按 id 缓存的 Hikari 连接池借出连接；调用方须 close 归还池。
     *
     * @throws SQLException if connection fails
     */
    public Connection getConnection(String connectionId) throws SQLException {
        return resolveDataSource(connectionId).getConnection();
    }

    /**
     * 配置变更或删除后丢弃缓存池，避免沿用旧 URL/凭据。
     */
    public void evictDataSource(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return;
        }
        DataSource removed = dataSources.remove(connectionId);
        if (removed instanceof HikariDataSource hikari) {
            hikari.close();
        }
    }

    private DataSource resolveDataSource(String connectionId) {
        return dataSources.computeIfAbsent(connectionId, this::createDataSource);
    }

    private DataSource createDataSource(String connectionId) {
        ConnectionSettings connectionSettings = connectionSettingsDao.findById(connectionId).orElseThrow();
        connectionSettings = decrypt(connectionSettings);
        HikariConfig config = new HikariConfig();
        config.setPoolName("kiwi-tool-jdbc-" + connectionId);
        config.setJdbcUrl(connectionSettings.getJdbcUrl());
        config.setUsername(connectionSettings.getUsername());
        config.setPassword(connectionSettings.getPassword());
        config.setMaximumPoolSize(ToolPoolMaxSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(30_000);
        return new HikariDataSource(config);
    }

    @Override
    public String group() {
        return "jdbc-connections";
    }

    @Override
    public Page<Dict> getDict(String pattern, Pageable pageable) {
        Query query = new Query();

        if( StringUtils.isNotBlank(pattern) ) {
            query.addCriteria(Criteria.where("name").regex(pattern));
        }

        return this.connectionSettingsDao.findBy(query, pageable).map(con -> {
            return new Dict(con.getId(), con.getName(), "", "");
        });
    }
}
