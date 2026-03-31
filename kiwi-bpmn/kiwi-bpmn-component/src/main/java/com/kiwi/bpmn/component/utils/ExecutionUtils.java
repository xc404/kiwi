package com.kiwi.bpmn.component.utils;



import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.variable.value.NumberValue;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.camunda.bpm.engine.variable.value.TypedValue;

import java.util.Optional;

public class ExecutionUtils
{

    public static  String getOutputVariableName(DelegateExecution execution, String variableName)
    {
        return getStringInputVariable(execution, variableName).orElse(null);
    }

    public static  Optional<String> getStringInputVariable(DelegateExecution execution, String variableName)
    {
        TypedValue variableTyped = execution.getVariableTyped(variableName);

        if(variableTyped == null) {
            return Optional.empty();
        }
        if( variableTyped instanceof StringValue stringValue ) {
            return Optional.ofNullable(stringValue.getValue());
        }
        return Optional.ofNullable(variableTyped.getValue()).map(Object::toString);
    }


    public static  Optional<Double> getNumberInputVariable(DelegateExecution execution, String variableName)
    {
        TypedValue variableTyped = execution.getVariableTyped(variableName);
        if(variableTyped == null) {
            return Optional.empty();
        }
        if( variableTyped instanceof NumberValue numberValue ) {
            return Optional.ofNullable(numberValue.getValue().doubleValue());
        }
        Object value = variableTyped.getValue();
        if( value instanceof Number number ) {
                return Optional.ofNullable(number.doubleValue());
        }
        if( value instanceof String stringValue ) {
            try {
                return Optional.ofNullable(Double.parseDouble(stringValue));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static  Optional<Integer> getIntInputVariable(DelegateExecution execution, String variableName)
    {
        return getNumberInputVariable(execution, variableName).map(Double::intValue);
    }

    public static Optional<Boolean> getBooleanInputVariable(DelegateExecution execution, String variableName)
    {
        TypedValue variableTyped = execution.getVariableTyped(variableName);
        if( variableTyped == null ) {
            return Optional.empty();
        }
        Object value = variableTyped.getValue();
        if( value instanceof Boolean booleanValue ) {
            return Optional.of(booleanValue);
        }
        if( value instanceof String stringValue ) {
            return Optional.of(Boolean.parseBoolean(stringValue));
        }
        return Optional.empty();
    }
}
