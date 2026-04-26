package com.kiwi.cryoems.bpm.movie.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MovieDelegateVariableService {

    public Object requiredVariable(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (value == null) {
            throw new IllegalArgumentException("流程变量缺失: " + name);
        }
        return value;
    }

    public Object optionalVariable(DelegateExecution execution, String name) {
        return execution.getVariable(name);
    }

    public void writeData(DelegateExecution execution, Map<String, Object> data) {
        writeFlatData(execution, data);
    }

    public void writeExceptionData(
            DelegateExecution execution,
            Exception ex,
            boolean retryable,
            boolean fatal
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exceptionMessage", ex.getMessage());
        data.put("exceptionType", ex.getClass().getName());
        data.put("retryable", retryable);
        data.put("fatal", fatal);
        writeFlatData(execution, data);
    }

    private void writeFlatData(DelegateExecution execution, Map<String, Object> data) {
        Map<String, Object> flatData = data == null ? Map.of() : data;
        for (Map.Entry<String, Object> entry : flatData.entrySet()) {
            String key = sanitizeKey(entry.getKey());
            if (!StringUtils.hasText(key)) {
                continue;
            }
            execution.setVariable( key, entry.getValue());
        }
    }

    private static String sanitizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.trim().replaceAll("[^a-zA-Z0-9_.-]", "_");
    }
}
