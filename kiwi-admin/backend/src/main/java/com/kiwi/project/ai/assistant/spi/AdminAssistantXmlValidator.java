package com.kiwi.project.ai.assistant.spi;

import com.kiwi.bpmn.assistant.spi.AssistantXmlValidator;
import com.kiwi.project.system.ai.BpmDesignerXmlValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@RequiredArgsConstructor
public class AdminAssistantXmlValidator implements AssistantXmlValidator {

    private final BpmDesignerXmlValidator bpmDesignerXmlValidator;

    @Override
    public void validate(String xml) {
        bpmDesignerXmlValidator.validate(xml);
    }
}
