package com.kiwi.project.ai.conversation;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.project.ai.conversation.AiConversationService.CreateConversationRequest;
import com.kiwi.project.ai.conversation.AiConversationService.UpdateConversationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 会话", description = "AI 助手历史会话持久化")
@RestController
@RequestMapping("/ai/conversations")
@RequiredArgsConstructor
public class AiConversationCtl extends BaseCtl {

    private final AiConversationService conversationService;

    @Operation(operationId = "ai_conversation_audit_page", summary = "审计：分页查询全部用户会话")
    @SaCheckPermission("ai:conversation:audit")
    @GetMapping("/audit")
    public Page<AiChatConversation> auditPage(
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Pageable pageable) {
        Page<AiChatConversation> result =
                conversationService.pageAudit(ownerId, scope, q, resolvePageable(page, size, pageable));
        stripMessagesForList(result);
        return result;
    }

    @Operation(operationId = "ai_conversation_page", summary = "分页查询当前用户会话")
    @SaCheckLogin
    @GetMapping("")
    public Page<AiChatConversation> page(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String scopeRef,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Pageable pageable) {
        String userId = requireUserId();
        Page<AiChatConversation> result =
                conversationService.pageForOwner(userId, scope, scopeRef, q, resolvePageable(page, size, pageable));
        stripMessagesForList(result);
        return result;
    }

    @Operation(operationId = "ai_conversation_get", summary = "获取会话详情（含消息）")
    @SaCheckLogin
    @GetMapping("/{id}")
    public AiChatConversation get(@PathVariable String id) {
        return conversationService.get(id, requireUserId(), hasAudit());
    }

    @Operation(operationId = "ai_conversation_create", summary = "创建会话")
    @SaCheckLogin
    @PostMapping("")
    public AiChatConversation create(@RequestBody CreateConversationRequest body) {
        return conversationService.create(requireUserId(), body != null ? body : new CreateConversationRequest());
    }

    @Operation(operationId = "ai_conversation_update", summary = "更新会话（标题或消息 append/replace）")
    @SaCheckLogin
    @PutMapping("/{id}")
    public AiChatConversation update(@PathVariable String id, @RequestBody UpdateConversationRequest body) {
        return conversationService.update(id, requireUserId(), body != null ? body : new UpdateConversationRequest());
    }

    @Operation(operationId = "ai_conversation_delete", summary = "删除会话")
    @SaCheckLogin
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        conversationService.delete(id, requireUserId());
    }

    private String requireUserId() {
        String userId = getCurrentUserId();
        if (StringUtils.isBlank(userId)) {
            throw new IllegalStateException("未登录");
        }
        return userId;
    }

    private boolean hasAudit() {
        try {
            return StpUtil.hasPermission("ai:conversation:audit");
        } catch (Exception e) {
            return false;
        }
    }

    private Pageable resolvePageable(Integer page, Integer size, Pageable pageable) {
        if (page != null || size != null) {
            int p = page != null && page >= 0 ? page : 0;
            int s = size != null && size > 0 ? Math.min(size, 100) : 20;
            return PageRequest.of(p, s);
        }
        if (pageable != null) {
            return pageable;
        }
        return PageRequest.of(0, 20);
    }

    private void stripMessagesForList(Page<AiChatConversation> page) {
        if (page == null) {
            return;
        }
        for (AiChatConversation c : page.getContent()) {
            c.setMessages(null);
        }
    }
}
