package com.kiwi.project.bpm.jdbc;

import com.kiwi.bpmn.component.jdbc.JdbcConnectionSupplier;
import com.kiwi.project.tools.jdbc.connection.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;

@Component
@RequiredArgsConstructor
public class KiwiJdbcConnectionSupplier implements JdbcConnectionSupplier {

    private final ConnectionService connectionService;

    @Override
    public Connection openConnection(String connectionId) throws SQLException {
        return connectionService.getConnection(connectionId);
    }
}
