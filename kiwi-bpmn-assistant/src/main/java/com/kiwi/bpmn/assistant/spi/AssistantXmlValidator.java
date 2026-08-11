package com.kiwi.bpmn.assistant.spi;

/**
 * BPMN XML 最小校验。模块提供 {@link com.kiwi.bpmn.assistant.DefaultAssistantXmlValidator}；
 * 宿主可用更严格实现覆盖（如包装 BpmDesignerXmlValidator）。
 */
public interface AssistantXmlValidator {

    /**
     * @throws IllegalArgumentException 非法或无法解析
     */
    void validate(String xml);
}
