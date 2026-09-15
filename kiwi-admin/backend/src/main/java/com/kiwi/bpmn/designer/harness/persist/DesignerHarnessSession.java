package com.kiwi.bpmn.designer.harness.persist;

import com.kiwi.bpmn.designer.harness.model.HarnessChatMessage;
import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("designer_harness_session")
public class DesignerHarnessSession extends BaseEntity<String> {
    private String initiatorUserId;
    private boolean running;
    private String errorMessage;
    private String selectedElementId;
    private List<HarnessChatMessage> messages = new ArrayList<>();
}