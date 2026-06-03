package com.kiwi.bpmn.component.jdbc;

import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcActivityTest {

    private Connection h2Connection;

    @BeforeEach
    void setUp() throws Exception {
        String db = "jdbc_activity_" + UUID.randomUUID();
        h2Connection = DriverManager.getConnection("jdbc:h2:mem:" + db + ";DB_CLOSE_DELAY=-1");
        try (Statement st = h2Connection.createStatement()) {
            st.execute("CREATE TABLE t (id INT PRIMARY KEY, val VARCHAR(20))");
            st.execute("INSERT INTO t VALUES (1, 'x')");
        }
    }

    @Test
    void execute_queryOne_writesResult() throws Exception {
        JdbcConnectionSupplier supplier = id -> h2Connection;
        JdbcActivity activity = spy(new JdbcActivity(supplier, new JdbcSqlSupport()));
        doNothing().when(activity).leave(any());

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("connection_id")).thenReturn(Variables.stringValue("conn-1"));
        when(execution.getVariableTyped("sql")).thenReturn(Variables.stringValue("SELECT id, val FROM t WHERE id = 1"));
        when(execution.getVariableTyped("operation")).thenReturn(Variables.stringValue("queryOne"));
        when(execution.getVariableTyped("params")).thenReturn(null);
        when(execution.getVariableTyped("max_rows")).thenReturn(null);
        when(execution.getVariableTyped("query_timeout_seconds")).thenReturn(null);
        when(execution.getVariableTyped("result")).thenReturn(Variables.stringValue("row"));

        activity.execute(execution);

        verify(execution).setVariable(eq("row"), any(Map.class));
        verify(activity).leave(execution);
    }
}
