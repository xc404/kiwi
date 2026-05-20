package com.kiwi.project.ai.conversation;

import com.kiwi.project.ai.AiChatMessage;
import com.kiwi.project.ai.AiChatProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiConversationService {

    private static final int TITLE_MAX = 40;
    private static final int PREVIEW_MAX = 120;
    private static final int SEARCH_TEXT_MAX = 4000;
    private static final Pattern SAFE_SCOPE = Pattern.compile("^(global|bpm-designer)$");

    private final AiChatConversationDao conversationDao;
    private final AiChatProperties aiChatProperties;

    public Page<AiChatConversation> pageForOwner(
            String ownerId,
            String scope,
            String scopeRef,
            String q,
            Pageable pageable) {
        return conversationDao.findBy(buildListQuery(ownerId, scope, scopeRef, q), pageable);
    }

    public Page<AiChatConversation> pageAudit(String ownerId, String scope, String q, Pageable pageable) {
        return conversationDao.findBy(buildListQuery(ownerId, scope, null, q), pageable);
    }

    public AiChatConversation get(String id, String userId, boolean hasAudit) {
        AiChatConversation doc = conversationDao.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        assertOwnerOrAudit(doc, userId, hasAudit);
        return doc;
    }

    public AiChatConversation create(String userId, CreateConversationRequest request) {
        String scope = normalizeScope(request.getScope());
        String scopeRef = normalizeScopeRef(request.getScopeRef());

        AiChatConversation doc = new AiChatConversation();
        doc.setId(new ObjectId().toHexString());
        doc.setOwnerId(userId);
        doc.setScope(scope);
        doc.setScopeRef(scopeRef);
        if (StringUtils.isNotBlank(request.getTitle())) {
            doc.setTitle(truncate(request.getTitle().trim(), TITLE_MAX));
        }
        List<AiChatMessage> initial = sanitizeMessages(request.getMessages());
        if (!initial.isEmpty()) {
            doc.setMessages(initial);
            applyMessageMetadata(doc);
        } else {
            doc.setMessages(new ArrayList<>());
            doc.setMessageCount(0);
            if (StringUtils.isBlank(doc.getTitle())) {
                doc.setTitle("新会话");
            }
        }
        return conversationDao.insert(doc);
    }

    public AiChatConversation update(String id, String userId, UpdateConversationRequest request) {
        AiChatConversation doc = conversationDao.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        assertOwner(doc, userId);

        if (StringUtils.isNotBlank(request.getTitle())) {
            doc.setTitle(truncate(request.getTitle().trim(), TITLE_MAX));
        }

        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            List<AiChatMessage> incoming = sanitizeMessages(request.getMessages());
            String mode = request.getMode() == null ? "append" : request.getMode().trim().toLowerCase();
            if ("replace".equals(mode)) {
                doc.setMessages(incoming);
            } else if ("append".equals(mode)) {
                List<AiChatMessage> merged = new ArrayList<>(doc.getMessages() != null ? doc.getMessages() : List.of());
                merged.addAll(incoming);
                doc.setMessages(trimToMaxMessages(merged));
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode 须为 append 或 replace");
            }
            applyMessageMetadata(doc);
        }

        return conversationDao.save(doc);
    }

    public void delete(String id, String userId) {
        AiChatConversation doc = conversationDao.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        assertOwner(doc, userId);
        conversationDao.deleteById(id);
    }

    private Query buildListQuery(String ownerId, String scope, String scopeRef, String q) {
        List<Criteria> criteria = new ArrayList<>();
        if (StringUtils.isNotBlank(ownerId)) {
            criteria.add(Criteria.where("ownerId").is(ownerId));
        }
        if (StringUtils.isNotBlank(scope)) {
            criteria.add(Criteria.where("scope").is(normalizeScope(scope)));
        }
        if (scopeRef != null) {
            if (scopeRef.isEmpty()) {
                criteria.add(new Criteria().orOperator(
                        Criteria.where("scopeRef").is(null),
                        Criteria.where("scopeRef").is("")));
            } else {
                criteria.add(Criteria.where("scopeRef").is(scopeRef));
            }
        }
        if (StringUtils.isNotBlank(q)) {
            String escaped = Pattern.quote(q.trim());
            criteria.add(new Criteria().orOperator(
                    Criteria.where("title").regex(escaped, "i"),
                    Criteria.where("searchText").regex(escaped, "i")));
        }
        Query query = criteria.isEmpty()
                ? new Query()
                : new Query(new Criteria().andOperator(criteria.toArray(Criteria[]::new)));
        query.with(Sort.by(Sort.Direction.DESC, "updatedTime"));
        return query;
    }

    private void assertOwner(AiChatConversation doc, String userId) {
        if (doc.getOwnerId() == null || !doc.getOwnerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该会话");
        }
    }

    private void assertOwnerOrAudit(AiChatConversation doc, String userId, boolean hasAudit) {
        if (hasAudit) {
            return;
        }
        assertOwner(doc, userId);
    }

    private String normalizeScope(String scope) {
        if (StringUtils.isBlank(scope)) {
            return AiChatConversation.SCOPE_GLOBAL;
        }
        String s = scope.trim();
        if (!SAFE_SCOPE.matcher(s).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope 无效");
        }
        return s;
    }

    private String normalizeScopeRef(String scopeRef) {
        if (scopeRef == null) {
            return null;
        }
        String ref = scopeRef.trim();
        return ref.isEmpty() ? null : ref;
    }

    private List<AiChatMessage> sanitizeMessages(List<AiChatMessage> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        int maxLen = Math.max(256, aiChatProperties.getConversationMaxContentLength());
        List<AiChatMessage> out = new ArrayList<>();
        for (AiChatMessage m : raw) {
            if (m == null || m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            String role = m.getRole() == null ? "user" : m.getRole().trim().toLowerCase();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            AiChatMessage copy = new AiChatMessage();
            copy.setRole(role);
            copy.setContent(truncateContent(m.getContent(), maxLen));
            out.add(copy);
        }
        return trimToMaxMessages(out);
    }

    private List<AiChatMessage> trimToMaxMessages(List<AiChatMessage> messages) {
        int max = Math.max(1, aiChatProperties.getConversationMaxMessages());
        if (messages.size() <= max) {
            return new ArrayList<>(messages);
        }
        return new ArrayList<>(messages.subList(messages.size() - max, messages.size()));
    }

    private String truncateContent(String content, int maxLen) {
        if (content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen) + "\n<!-- …已截断… -->";
    }

    private void applyMessageMetadata(AiChatConversation doc) {
        List<AiChatMessage> msgs = doc.getMessages() != null ? doc.getMessages() : List.of();
        doc.setMessages(new ArrayList<>(msgs));
        doc.setMessageCount(msgs.size());
        if (StringUtils.isBlank(doc.getTitle()) || "新会话".equals(doc.getTitle())) {
            doc.setTitle(deriveTitle(msgs));
        }
        doc.setLastMessagePreview(derivePreview(msgs));
        doc.setSearchText(deriveSearchText(msgs));
    }

    private String deriveTitle(List<AiChatMessage> msgs) {
        for (AiChatMessage m : msgs) {
            if ("user".equals(m.getRole()) && StringUtils.isNotBlank(m.getContent())) {
                return truncate(m.getContent().trim().replaceAll("\\s+", " "), TITLE_MAX);
            }
        }
        return "新会话";
    }

    private String derivePreview(List<AiChatMessage> msgs) {
        if (msgs.isEmpty()) {
            return "";
        }
        AiChatMessage last = msgs.get(msgs.size() - 1);
        String c = last.getContent() != null ? last.getContent().trim().replaceAll("\\s+", " ") : "";
        return truncate(c, PREVIEW_MAX);
    }

    private String deriveSearchText(List<AiChatMessage> msgs) {
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, msgs.size() - 20);
        for (int i = from; i < msgs.size(); i++) {
            AiChatMessage m = msgs.get(i);
            if (m.getContent() != null && !m.getContent().isBlank()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(m.getContent().trim().replaceAll("\\s+", " "));
            }
        }
        return truncate(sb.toString(), SEARCH_TEXT_MAX);
    }

    private String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    @Data
    public static class CreateConversationRequest {
        private String scope;
        private String scopeRef;
        private String title;
        private List<AiChatMessage> messages;
    }

    @Data
    public static class UpdateConversationRequest {
        /** append | replace */
        private String mode;
        private String title;
        private List<AiChatMessage> messages;
    }
}
