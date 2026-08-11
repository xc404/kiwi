package com.kiwi.project.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantCatalog;
import com.kiwi.bpmn.assistant.AssistantKeywordExtractor;
import com.kiwi.bpmn.assistant.AssistantPlan;
import com.kiwi.bpmn.assistant.AssistantPlanCompiler;
import com.kiwi.bpmn.assistant.AssistantPlanGenerateService;
import com.kiwi.bpmn.assistant.AssistantProperties;
import com.kiwi.bpmn.assistant.AssistantRuleSet;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.bpmn.assistant.AssistantWorkflowValidator;
import com.kiwi.bpmn.assistant.DefaultAssistantXmlValidator;
import com.kiwi.bpmn.assistant.WriteWorkflowSession;
import com.kiwi.bpmn.assistant.spi.AssistantComponentLookup;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import com.kiwi.project.bpm.service.BpmRemoteMarketInstallService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WriteWorkflowOrchestratorTest {

    @Mock
    AssistantKeywordExtractor keywordExtractor;
    @Mock
    AssistantCatalogContextBuilder catalogContextBuilder;
    @Mock
    AssistantPlanGenerateService planGenerateService;
    @Mock
    AssistantComponentLookup componentLookup;
    @Mock
    BpmRemoteMarketInstallService installService;
    @Mock
    BpmProcessDefinitionDao processDao;
    @Mock
    BpmProcessDefinitionService processDefinitionService;

    private ObjectMapper objectMapper;
    private AssistantWorkflowValidator validator;
    private WriteWorkflowOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AssistantRuleSet rules = new AssistantRuleSet(objectMapper);
        rules.init();
        org.mockito.Mockito.lenient().when(componentLookup.exists(anyString())).thenReturn(true);
        org.mockito.Mockito.lenient().when(componentLookup.requiredInputKeys(anyString())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(componentLookup.pluginMissingHint(anyString())).thenReturn(Optional.empty());
        validator = new AssistantWorkflowValidator(
                new DefaultAssistantXmlValidator(),
                componentLookup,
                new AssistantProperties(),
                objectMapper,
                rules);
        orchestrator = new WriteWorkflowOrchestrator(
                keywordExtractor,
                catalogContextBuilder,
                planGenerateService,
                validator,
                objectMapper,
                installService,
                processDao,
                processDefinitionService);
    }

    @Test
    void runTurn_pass_thenConfirmPreview_saves() throws Exception {
        when(keywordExtractor.extractAsJson(anyString())).thenReturn("[\"order\"]");
        when(catalogContextBuilder.buildAsJson(anyString(), any())).thenReturn(
                objectMapper.writeValueAsString(new AssistantCatalog()));

        AssistantPlan plan = new AssistantPlan();
        plan.setProcessId("order_flow");
        plan.setNodes(List.of(
                node("StartEvent_1", "startEvent"),
                node("UserTask_1", "userTask"),
                node("EndEvent_1", "endEvent")));
        plan.setFlows(List.of(
                flow("Flow_1", "StartEvent_1", "UserTask_1"),
                flow("Flow_2", "UserTask_1", "EndEvent_1")));
        String xml = new AssistantPlanCompiler(objectMapper).compile(plan, new AssistantCatalog());

        AssistantPlanGenerateService.GenerateResult gen = new AssistantPlanGenerateService.GenerateResult();
        gen.setCandidateXml(xml);
        gen.setPlanIrJson("{}");
        gen.setAssistantReply("已生成订单流程");
        when(planGenerateService.generate(anyString(), anyString(), isNull(), isNull(), isNull()))
                .thenReturn(gen);

        WriteWorkflowSession session = WriteWorkflowSession.newSession(
                "创建订单流程", "target-1", null, null, "user-1");
        orchestrator.runTurn(session);
        assertEquals(AssistantVariables.StageAwaitPreview, session.getStage());
        assertEquals(AssistantVariables.DispatchPass, session.getDispatchCode());

        BpmProcess target = new BpmProcess();
        target.setId("target-1");
        when(processDao.findById("target-1")).thenReturn(Optional.of(target));
        orchestrator.confirmPreview(session, true);
        assertEquals(AssistantVariables.StageDone, session.getStage());
        assertFalse(session.isActive());
        assertEquals(xml, target.getBpmnXml());
        verify(processDao).save(eq(target));
    }

    private AssistantPlan.Node node(String id, String type) {
        AssistantPlan.Node n = new AssistantPlan.Node();
        n.setId(id);
        n.setType(type);
        return n;
    }

    private AssistantPlan.Flow flow(String id, String source, String target) {
        AssistantPlan.Flow f = new AssistantPlan.Flow();
        f.setId(id);
        f.setSourceRef(source);
        f.setTargetRef(target);
        return f;
    }
}
