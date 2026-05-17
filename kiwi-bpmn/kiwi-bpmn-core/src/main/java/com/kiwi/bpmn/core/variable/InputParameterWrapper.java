package com.kiwi.bpmn.core.variable;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.impl.core.variable.mapping.InputParameter;
import org.camunda.bpm.engine.impl.core.variable.scope.AbstractVariableScope;

@Slf4j
public class InputParameterWrapper extends InputParameter
{
    public InputParameterWrapper(InputParameter delegate) {
        super(delegate.getName(), delegate.getValueProvider());
    }

    @Override
    protected void execute(AbstractVariableScope innerScope, AbstractVariableScope outerScope) {
            super.execute(innerScope,innerScope);
    }
}
