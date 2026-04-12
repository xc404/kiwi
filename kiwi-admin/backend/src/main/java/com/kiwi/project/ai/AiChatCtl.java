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

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String text = aiChatService.complete(request.getMessages());
        ChatResponse out = new ChatResponse();
        out.setContent(text);
        return out;
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
