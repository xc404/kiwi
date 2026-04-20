package com.kiwi.project.ai;

import cn.dev33.satoken.annotation.SaCheckLogin;
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

    @Data
    public static class ChatRequest {
        private List<AiChatMessage> messages;
    }

    @Data
    public static class ChatResponse {
        private String content;
    }
}
