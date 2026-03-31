package com.kiwi.project.tools.jdbc.connection.service;

import com.kiwi.framework.security.PasswordService;
import com.kiwi.project.system.spi.Dict;
import com.kiwi.project.system.spi.DictProvider;
import com.kiwi.project.tools.jdbc.connection.dao.ConnectionSettingsDao;
import com.kiwi.project.tools.jdbc.connection.entity.ConnectionSettings;
import lombok.RequiredArgsConstructor;
import net.dreamlu.mica.core.utils.AesUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Service
@RequiredArgsConstructor
public class ConnectionService implements DictProvider
{
    private final ConnectionSettingsDao connectionSettingsDao;
    private final PasswordService passwordService;

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
     * Tests the JDBC connection using the provided settings.
     *
     * @throws SQLException if connection fails
     */
    public Connection getConnection(String connectionId) throws SQLException {
        ConnectionSettings connectionSettings = this.connectionSettingsDao.findById(connectionId).orElseThrow();
        // Decrypt password if needed
        connectionSettings = this.decrypt(connectionSettings);
        DataSource dataSource = DataSourceBuilder.create()
                .type(SimpleDriverDataSource.class)
                .url(connectionSettings.getJdbcUrl()).username(connectionSettings.getUsername())
                .password(connectionSettings.getPassword())
                .build();
        // Attempt to connect
        return dataSource.getConnection();
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
