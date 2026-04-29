package com.kiwi.bpmn.component.activity;

import com.sun.net.httpserver.HttpServer;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpRequestActivityTest {

    @Test
    void parseHttpUri_rejectsNonHttp() {
        assertThrows(IllegalArgumentException.class, () -> HttpRequestActivity.parseHttpUri("ftp://a/b"));
    }

    @Test
    void parseHeadersDocument_invalidJson() {
        assertThrows(IllegalArgumentException.class, () -> HttpRequestActivity.parseHeadersDocument("not-json"));
    }

    @Test
    void execute_get_localServer_writesOutputs() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] payload = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        server.createContext(
                "/test",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Test", "a");
                    exchange.sendResponseHeaders(200, payload.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(payload);
                    }
                });
        server.start();
        try {
            int port = server.getAddress().getPort();
            String url = "http://127.0.0.1:" + port + "/test";

            ActivityExecution execution = mock(ActivityExecution.class);
            when(execution.getVariableTyped("url")).thenReturn(Variables.stringValue(url));
            when(execution.getVariableTyped("method")).thenReturn(null);
            when(execution.getVariableTyped("connectTimeoutSeconds")).thenReturn(null);
            when(execution.getVariableTyped("readTimeoutSeconds")).thenReturn(null);
            when(execution.getVariableTyped("headers")).thenReturn(null);
            when(execution.getVariableTyped("body")).thenReturn(null);
            when(execution.getVariableTyped("statusCode")).thenReturn(Variables.stringValue("sc"));
            when(execution.getVariableTyped("responseBody")).thenReturn(Variables.stringValue("rb"));
            when(execution.getVariableTyped("responseHeaders")).thenReturn(Variables.stringValue("rh"));

            HttpRequestActivity activity = spy(new HttpRequestActivity());
            doNothing().when((AbstractBpmnActivityBehavior) activity).leave(any(ActivityExecution.class));

            activity.execute(execution);

            verify(execution).setVariable(eq("sc"), eq(200));
            verify(execution).setVariable(eq("rb"), eq("{\"ok\":true}"));
            verify(execution)
                    .setVariable(
                            eq("rh"),
                            org.mockito.ArgumentMatchers.argThat(
                                    (Object json) ->
                                            json != null
                                                    && json.toString().toLowerCase().contains("x-test")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void execute_invalidHeadersFailsBeforeNetwork() {
        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("url")).thenReturn(Variables.stringValue("http://127.0.0.1:9/nope"));
        when(execution.getVariableTyped("method")).thenReturn(null);
        when(execution.getVariableTyped("connectTimeoutSeconds")).thenReturn(null);
        when(execution.getVariableTyped("readTimeoutSeconds")).thenReturn(null);
        when(execution.getVariableTyped("body")).thenReturn(null);
        when(execution.getVariableTyped("headers")).thenReturn(Variables.stringValue("["));
        HttpRequestActivity activity = new HttpRequestActivity();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> activity.execute(execution));
        assertTrue(ex.getMessage().contains("headers"));
    }
}
