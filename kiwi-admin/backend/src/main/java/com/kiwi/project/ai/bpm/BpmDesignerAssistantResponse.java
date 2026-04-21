package com.kiwi.project.ai.bpm;

import lombok.Data;

import java.util.List;

@Data
public class BpmDesignerAssistantResponse {

    private String content;
    private List<BpmDesignerAction> actions;
}
