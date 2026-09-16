package com.kiwi.bpmn.designer.harness;

import com.kiwi.bpmn.designer.harness.model.HarnessChatMessage;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DesignerHarnessStatus {
    private String targetProcessId;
    private boolean running;
    private boolean inputEnabled;
    private String errorMessage;
    private String bpmnXml;
    private List<HarnessChatMessage> messages = new ArrayList<>();
}