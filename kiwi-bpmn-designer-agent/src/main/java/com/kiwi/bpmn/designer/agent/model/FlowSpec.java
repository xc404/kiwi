package com.kiwi.bpmn.designer.agent.model;

import lombok.Data;

@Data
public class FlowSpec {
    private String id;
    private String sourceRef;
    private String targetRef;
    private String condition;
}
