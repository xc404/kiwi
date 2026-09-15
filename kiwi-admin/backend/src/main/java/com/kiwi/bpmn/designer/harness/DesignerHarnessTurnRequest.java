package com.kiwi.bpmn.designer.harness;

import lombok.Data;

@Data
public class DesignerHarnessTurnRequest {
    private String targetProcessId;
    private String message;
    private String canvasBpmnXml;
    private String selectedElementId;
}