package com.kiwi.bpmn.component.utils;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.variable.value.NumberValue;
import org.operaton.bpm.engine.variable.value.StringValue;
import org.operaton.bpm.engine.variable.value.TypedValue;

import java.util.Map;
import java.util.Optional;

public class ExecutionUtils
{

    public static String getOutputVariableName(DelegateExecution execution, String variableName)
    {
        return getStringInputVariable(execution, variableName).orElse(null);
    }

    public static Optional<String> getStringInputVariable(DelegateExecution execution, String variableName)
    {
        return getInputVariable(execution, variableName).flatMap(ExecutionUtils::toOptionalString);
    }

    /**
     * 按点分路径读取流程变量，例如 {@code task.config_id}、{@code movie.data_id}。
     * <p>
     * 无 {@code .} 时与 {@link #getStringInputVariable} 行为一致；含 {@code .} 时按段从 Map 嵌套解析，
     * 解析失败时再尝试将完整 path 作为顶层变量名（兼容字面量带点名的变量）。
     */
    public static Optional<String> getStringInputVariableAtPath(DelegateExecution execution, String path)
    {
        return getInputVariableAtPath(execution, path).flatMap(ExecutionUtils::toOptionalString);
    }

    public static Optional<Object> getInputVariableAtPath(DelegateExecution execution, String path)
    {
        if( path == null || path.isBlank() ) {
            return Optional.empty();
        }
        String trimmed = path.trim();
        if( !trimmed.contains(".") ) {
            return getInputVariable(execution, trimmed);
        }
        Optional<Object> nested = resolveNestedPath(execution, trimmed);
        if( nested.isPresent() ) {
            return nested;
        }
        return getInputVariable(execution, trimmed);
    }

    public static Optional<Double> getNumberInputVariable(DelegateExecution execution, String variableName)
    {
        return getInputVariable(execution, variableName).flatMap(ExecutionUtils::toOptionalDouble);
    }

    public static Optional<Double> getNumberInputVariableAtPath(DelegateExecution execution, String path)
    {
        return getInputVariableAtPath(execution, path).flatMap(ExecutionUtils::toOptionalDouble);
    }

    public static Optional<Integer> getIntInputVariable(DelegateExecution execution, String variableName)
    {
        return getNumberInputVariable(execution, variableName).map(Double::intValue);
    }

    public static Optional<Boolean> getBooleanInputVariable(DelegateExecution execution, String variableName)
    {
        return getInputVariable(execution, variableName).flatMap(ExecutionUtils::toOptionalBoolean);
    }

    public static Optional<Boolean> getBooleanInputVariableAtPath(DelegateExecution execution, String path)
    {
        return getInputVariableAtPath(execution, path).flatMap(ExecutionUtils::toOptionalBoolean);
    }

    private static Optional<Object> getInputVariable(DelegateExecution execution, String variableName)
    {
        TypedValue variableTyped = execution.getVariableTyped(variableName);
        if( variableTyped == null ) {
            return Optional.empty();
        }
        if( variableTyped instanceof StringValue stringValue ) {
            return Optional.ofNullable(stringValue.getValue());
        }
        return Optional.ofNullable(variableTyped.getValue());
    }

    private static Optional<Object> resolveNestedPath(DelegateExecution execution, String path)
    {
        String[] segments = path.split("\\.");
        if( segments.length < 2 ) {
            return Optional.empty();
        }
        Optional<Object> current = getInputVariable(execution, segments[0]);
        for( int i = 1; i < segments.length; i++ ) {
            String segment = segments[i];
            if( segment.isEmpty() ) {
                return Optional.empty();
            }
            if( current.isEmpty() ) {
                return Optional.empty();
            }
            current = getNestedProperty(current.get(), segment);
        }
        return current;
    }

    private static Optional<Object> getNestedProperty(Object container, String key)
    {
        if( container instanceof Map<?, ?> map ) {
            return Optional.ofNullable(map.get(key));
        }
        return Optional.empty();
    }

    private static Optional<String> toOptionalString(Object value)
    {
        if( value == null ) {
            return Optional.empty();
        }
        return Optional.of(value.toString());
    }

    private static Optional<Double> toOptionalDouble(Object value)
    {
        if( value == null ) {
            return Optional.empty();
        }
        if( value instanceof Number number ) {
            return Optional.of(number.doubleValue());
        }
        if( value instanceof String stringValue ) {
            try {
                return Optional.of(Double.parseDouble(stringValue));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Boolean> toOptionalBoolean(Object value)
    {
        if( value == null ) {
            return Optional.empty();
        }
        if( value instanceof Boolean booleanValue ) {
            return Optional.of(booleanValue);
        }
        if( value instanceof String stringValue ) {
            return Optional.of(Boolean.parseBoolean(stringValue));
        }
        return Optional.empty();
    }
}
