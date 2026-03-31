package com.kiwi.bpmn.component.utils;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.variable.value.NumberValue;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.camunda.bpm.engine.variable.value.TypedValue;

import java.util.Optional;

public class ExternalTaskUtils
{


    public static  String getOutputVariableName(ExternalTask execution, String variableName)
    {
        return getStringInputVariable(execution, variableName).orElse(null);
    }

    public static Optional<String> getStringInputVariable(ExternalTask execution, String variableName)
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


    public static  Optional<Number> getNumberInputVariable(ExternalTask execution, String variableName)
    {
        TypedValue variableTyped = execution.getVariableTyped(variableName);
        if(variableTyped == null) {
            return Optional.empty();
        }
        if( variableTyped instanceof NumberValue numberValue ) {
            return Optional.ofNullable(numberValue.getValue());
        }
        Object value = variableTyped.getValue();
        if( value instanceof Number number ) {
            return Optional.ofNullable(number);
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

    public static Optional<Boolean> getBooleanInputVariable(ExternalTask execution, String variableName)
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
