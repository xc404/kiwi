package com.kiwi.project.bpm.designer.agent;

import com.kiwi.bpmn.designer.agent.mcp.DesignerAgentToolTraceContext;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * 在 ToolCallingAdvisor 循环内观测工具调用意图（与 TracingToolCallbacks 互补）。
 */
public class DesignerAgentToolTraceAdvisor implements CallAdvisor, StreamAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        observeRequest(request);
        ChatClientResponse response = chain.nextCall(request);
        observeResponse(response);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        observeRequest(request);
        return chain.nextStream(request).doOnNext(this::observeResponse);
    }

    @Override
    public String getName() {
        return DesignerAgentToolTraceAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 400;
    }

    private void observeRequest(ChatClientRequest request) {
        if (request == null || request.prompt() == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Message message : request.prompt().getInstructions()) {
            if (message instanceof ToolResponseMessage toolResponse) {
                for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                    if (seen.add(response.name())) {
                        DesignerAgentToolTraceContext.emitToolEnd(
                                response.name(),
                                DesignerAgentToolTraceContext.summarizeToolResult(response.responseData()));
                    }
                }
            }
        }
    }

    private void observeResponse(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null || !response.chatResponse().hasToolCalls()) {
            return;
        }
        AssistantMessage output = response.chatResponse().getResult().getOutput();
        if (output.getToolCalls() == null) {
            return;
        }
        output.getToolCalls().forEach(call -> DesignerAgentToolTraceContext.emitToolStart(
                call.name(), call.arguments()));
    }
}
