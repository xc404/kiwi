package com.kiwi.project.ai;

import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "AI 对话", description = "聊天补全与统一助手")
public class AiChatCtl {

    private final AiChatService aiChatService;
    private final AiAssistantService aiAssistantService;

    @Operation(operationId = "ai_chat", summary = "AI 聊天补全（单轮文本回复）")
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String text = aiChatService.complete(request.getMessages());
        ChatResponse out = new ChatResponse();
        out.setContent(text);
        return out;
    }

    /**
     * 统一助手对话：模型自行选用 MCP 工具；响应中的 actions 由工具登记（菜单跳转、BPM 设计器建议等）。
     */
    @Operation(operationId = "ai_assistant", summary = "统一助手对话（可选用 MCP 工具，响应含客户端 actions）")
    @PostMapping("/assistant")
    public AiAssistantResponse assistant(@RequestBody ChatRequest request) {
        return aiAssistantService.run(request.getMessages());
    }

    @Data
    @Schema(description = "AI 聊天请求")
    public static class ChatRequest {
        @Schema(description = "对话消息列表")
        private List<AiChatMessage> messages;
    }

    @Data
    @Schema(description = "AI 聊天响应")
    public static class ChatResponse {
        @Schema(description = "助手回复文本")
        private String content;
    }
}
