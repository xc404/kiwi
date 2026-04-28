package com.kiwi.bpmn.core.variable;

import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.core.variable.mapping.InputParameter;
import org.camunda.bpm.engine.impl.core.variable.mapping.IoMapping;
import org.camunda.bpm.engine.impl.core.variable.mapping.OutputParameter;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InputMappingBpmnParseListener extends AbstractBpmnParseListener
{
    @Override
    public void parseIoMapping(Element extensionElements, ActivityImpl activity, IoMapping inputOutput) {
        super.parseIoMapping(extensionElements, activity, inputOutput);
        List<InputParameter> list = inputOutput.getInputParameters().stream().map(inputParameter -> {
            return (InputParameter)new InputParameterWrapper(inputParameter);
        }).toList();
        inputOutput.setInputParameters(list);

        List<OutputParameter> out = inputOutput.getOutputParameters().stream()
                .map(p -> (OutputParameter) new OutputParameterWrapper((OutputParameter) p))
                .toList();
        // Camunda 7.x API 方法名拼写为 setOuputParameters
        inputOutput.setOuputParameters(out);
    }
}
