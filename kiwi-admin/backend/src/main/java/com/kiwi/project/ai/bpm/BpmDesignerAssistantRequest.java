package com.kiwi.project.ai.bpm;

import com.kiwi.project.ai.AiChatMessage;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BpmDesignerAssistantRequest {

    private List<AiChatMessage> messages;

    /** 当前编辑的流程定义 id，服务端用于校验存在性及拼上下文。 */
    private String processId;

    /**
     * 可选：画布当前 BPMN（含未保存修改）。不传则使用库中已保存版本拼上下文。
     */
    private String bpmnXml;

    /**
     * 可选：由前端声明当前支持的动作能力，避免前后端命令写死耦合。
     */
    private ClientCapabilities clientCapabilities;

    @Data
    public static class ClientCapabilities {
        private List<String> toolbarCommands;
        private Boolean allowBpmnXml;
        private Boolean allowAppendComponent;
        private Boolean allowNavigate;
        /** 可选：前端当前可见组件 id -> 名称。供模型挑选更贴近上下文。 */
        private Map<String, String> availableComponents;
    }
}
