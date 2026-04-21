package com.kiwi.project.ai.bpm;

import com.kiwi.project.ai.AiChatMessage;
import lombok.Data;

import java.util.List;

@Data
public class BpmDesignerAssistantRequest {

    private List<AiChatMessage> messages;

    /** 当前编辑的流程定义 id，服务端用于校验存在性及拼上下文。 */
    private String processId;

    /**
     * 可选：画布当前 BPMN（含未保存修改）。不传则使用库中已保存版本拼上下文。
     */
    private String bpmnXml;
}
