package com.kiwi.bpmn.designer.agent.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ClarificationQuestion {
    private String id;
    private String prompt;
    private List<ClarificationOption> options = new ArrayList<>();
    private boolean allowMultiple;
}
