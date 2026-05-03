package com.kiwi.bpmn.core.variable;

import org.camunda.bpm.engine.impl.core.variable.mapping.OutputParameter;
import org.camunda.bpm.engine.impl.core.variable.scope.AbstractVariableScope;

/**
 * Camunda 默认 {@link OutputParameter} 只把值写到父作用域；当
 * {@link AbstractVariableScope#getParentVariableScope()} 为 null 时会在
 * {@code outerScope.setVariable} 处 NPE。
 * <p>
 * 本类先写当前（内层）作用域，再在父作用域非 null 时写父作用域；父为 null 时仅保留本作用域上的变量，不抛错。
 */
public class OutputParameterWrapper extends OutputParameter
{

    public OutputParameterWrapper(OutputParameter delegate) {
        super(delegate.getName(), delegate.getValueProvider());
    }

    @Override
    protected void execute(AbstractVariableScope innerScope, AbstractVariableScope outerScope) {
        Object value = getValueProvider().getValue(innerScope);
        innerScope.setVariable(getName(), value);
        if (outerScope != null) {
            outerScope.setVariable(getName(), value);
        }
    }
}
