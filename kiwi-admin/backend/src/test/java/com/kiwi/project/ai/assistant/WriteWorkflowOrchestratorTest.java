package com.kiwi.project.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantBpmnToPlan;
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
import com.kiwi.project.bpm.service.BpmRemoteMarketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WriteWorkflowOrchestratorTest {

    @Mock
    AssistantKeywordExtractor keywordExtractor;
    @Mock
    AssistantPlanGenerateService planGenerateService;
    @Mock
    AssistantComponentLookup componentLookup;
    @Mock
    BpmRemoteMarketInstallService installService;
    @Mock
    ObjectProvider<BpmRemoteMarketService> remoteMarketServiceProvider;
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
        org.mockito.Mockito.lenient().when(componentLookup.resolveDelegateExpression(anyString()))
                .thenReturn(Optional.of("${shell}"));
        org.mockito.Mockito.lenient().when(remoteMarketServiceProvider.getIfAvailable()).thenReturn(null);
        validator = new AssistantWorkflowValidator(
                new DefaultAssistantXmlValidator(),
                componentLookup,
                new AssistantProperties(),
                objectMapper,
                rules);
        orchestrator = new WriteWorkflowOrchestrator(
                keywordExtractor,
                planGenerateService,
                validator,
                objectMapper,
                installService,
                remoteMarketServiceProvider,
                processDao,
                processDefinitionService,
                new AssistantBpmnToPlan());
    }

    @Test
    void runTurn_pass_thenConfirmPreview_saves() throws Exception {
        when(keywordExtractor.extractAsJson(anyString())).thenReturn("[\"order\"]");

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
        when(planGenerateService.generate(anyString(), isNull(), isNull(), isNull()))
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
        verify(processDao).save(any(BpmProcess.class));
    }

    @Test
    void runTurn_ambiguousTidy_asksWithoutGenerate() {
        WriteWorkflowSession session = WriteWorkflowSession.newSession(
                "整理这个流程", "target-1", null, "<bpmn/>", "user-1");
        orchestrator.runTurn(session);
        assertEquals(AssistantVariables.StageAwaitAsk, session.getStage());
        assertTrue(session.getAskMessage().contains("整理"));
    }

    @Test
    void runTurn_unauthorizedComponentSwap_asksAndRestoresBase() {
        String base = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:kiwi="http://kiwi.com/bpmn"
                                  id="Definitions_1" targetNamespace="tns">
                  <bpmn:process id="p1" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1"/>
                    <bpmn:serviceTask id="Activity_1" name="命令行" kiwi:componentId="plugin_shell"/>
                    <bpmn:endEvent id="EndEvent_1"/>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_1"/>
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_1" targetRef="EndEvent_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
        String swapped = base.replace("plugin_shell", "classpath_httpRequest");
        AssistantPlanGenerateService.GenerateResult gen = new AssistantPlanGenerateService.GenerateResult();
        gen.setCandidateXml(swapped);
        gen.setPlanIrJson("{}");
        gen.setAssistantReply("已重建为消息发布");
        when(keywordExtractor.extractAsJson(anyString())).thenReturn("[\"整理\"]");
        when(planGenerateService.generate(anyString(), isNull(), anyString(), isNull())).thenReturn(gen);

        WriteWorkflowSession session = WriteWorkflowSession.newSession(
                "优化一下节点命名", "target-1", null, base, "user-1");
        // 「优化一下节点命名」不含 AmbiguousTidy 的「整理|规范|清理|优化布局」——「优化」alone may not match
        // Use scenario that generates but doesn't allow replace
        session.setScenario("优化节点命名");
        orchestrator.runTurn(session);

        assertEquals(AssistantVariables.StageAwaitAsk, session.getStage());
        assertTrue(session.getCandidateXml().contains("plugin_shell"));
        assertTrue(session.getAssistantReply().contains("擅自更换")
                || session.getAskMessage().contains("组件"));
    }

    private static AssistantPlan.Node node(String id, String type) {
        AssistantPlan.Node n = new AssistantPlan.Node();
        n.setId(id);
        n.setType(type);
        return n;
    }

    private static AssistantPlan.Flow flow(String id, String source, String target) {
        AssistantPlan.Flow f = new AssistantPlan.Flow();
        f.setId(id);
        f.setSourceRef(source);
        f.setTargetRef(target);
        return f;
    }
}
