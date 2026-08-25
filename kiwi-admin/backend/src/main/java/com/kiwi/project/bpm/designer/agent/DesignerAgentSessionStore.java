package com.kiwi.project.bpm.designer.agent;

import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentChatMessage;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentConversationHistoryUtils;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentRun;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DesignerAgentSessionStore {

    private final MongoTemplate mongoTemplate;

    public Optional<DesignerAgentSessionDoc> findByTarget(String targetProcessId) {
        if (StringUtils.isBlank(targetProcessId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findById(targetProcessId, DesignerAgentSessionDoc.class));
    }

    public void saveSession(DesignerAgentRun run) {
        if (run == null || StringUtils.isBlank(run.getTargetProcessId()) || StringUtils.isBlank(run.getRunId())) {
            return;
        }
        DesignerAgentSessionDoc doc = mongoTemplate.findById(run.getTargetProcessId(), DesignerAgentSessionDoc.class);
        if (doc == null) {
            doc = new DesignerAgentSessionDoc();
            doc.setTargetProcessId(run.getTargetProcessId());
        }
        doc.setRunId(run.getRunId());
        doc.setMessages(new ArrayList<>(DesignerAgentConversationHistoryUtils.toChatMessages(run.getConversationHistory())));
        doc.setUpdatedAt(new Date());
        mongoTemplate.save(doc);
    }

    public void deleteByTarget(String targetProcessId) {
        if (StringUtils.isBlank(targetProcessId)) {
            return;
        }
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(targetProcessId)),
                DesignerAgentSessionDoc.class);
    }

    public List<DesignerAgentChatMessage> messagesForTarget(String targetProcessId) {
        return findByTarget(targetProcessId).map(DesignerAgentSessionDoc::getMessages).orElse(List.of());
    }
}
