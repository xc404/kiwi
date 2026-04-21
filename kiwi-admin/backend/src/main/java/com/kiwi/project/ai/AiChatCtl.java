package com.kiwi.project.ai;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.project.ai.bpm.BpmDesignerAssistantRequest;
import com.kiwi.project.ai.bpm.BpmDesignerAssistantResponse;
import com.kiwi.project.ai.bpm.BpmDesignerAssistantService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@SaCheckLogin
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatCtl {

    private final AiChatService aiChatService;
    private final AiAssistantService aiAssistantService;
    private final BpmDesignerAssistantService bpmDesignerAssistantService;

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String text = aiChatService.complete(request.getMessages());
        ChatResponse out = new ChatResponse();
        out.setContent(text);
        return out;
    }

    /**
     * 带服务端动作（如创建字典后返回跳转指令）的助手对话，供前端执行导航等。
     */
    @PostMapping("/assistant")
    public AiAssistantResponse assistant(@RequestBody ChatRequest request) {
        return aiAssistantService.run(request.getMessages());
    }

    /**
     * BPM 设计器专用：返回自然语言 + 结构化动作（工具栏、导入 XML、追加组件、跳转等）。
     */
    @PostMapping("/bpm-designer")
    public BpmDesignerAssistantResponse bpmDesigner(@RequestBody BpmDesignerAssistantRequest request) {
        return bpmDesignerAssistantService.run(request);
    }

    @Data
    public static class ChatRequest {
        private List<AiChatMessage> messages;
    }

    @Data
    public static class ChatResponse {
        private String content;
    }
}
