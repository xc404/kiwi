package com.kiwi.bpmn.designer.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantBpmnToPlan;
import com.kiwi.bpmn.assistant.AssistantPlanCompiler;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.bpmn.assistant.AssistantWorkflowValidator;
import com.kiwi.bpmn.designer.agent.DesignerAgentProperties;
import com.kiwi.bpmn.designer.agent.apply.EditPlanApplicator;
import com.kiwi.bpmn.designer.agent.apply.PlanSkipEvaluator;
import com.kiwi.bpmn.designer.agent.mcp.DesignerAgentToolTraceContext;
import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import com.kiwi.bpmn.designer.agent.model.AgentStreamEvent;
import com.kiwi.bpmn.designer.agent.model.EditOperation;
import com.kiwi.bpmn.designer.agent.model.EditPlan;
import com.kiwi.bpmn.designer.agent.model.FlowSpec;
import com.kiwi.bpmn.designer.agent.model.NodeSpec;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentPlanGenerator.GenerateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesignerAgentOrchestratorSseTest {

    private static final String MinimalBpmn = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
              xmlns:kiwi="http://kiwi.com/bpmn" id="D1" targetNamespace="http://kiwi.io/test">
              <bpmn:process id="P1" name="Test" isExecutable="true">
                <bpmn:startEvent id="Start_1"/>
                <bpmn:endEvent id="End_1"/>
                <bpmn:sequenceFlow id="F1" sourceRef="Start_1" targetRef="End_1"/>
              </bpmn:process>
            </bpmn:definitions>
            """;

    @Mock
    private DesignerAgentPlanGenerator planGenerator;

    @Mock
    private AssistantWorkflowValidator workflowValidator;

    private DesignerAgentOrchestrator orchestrator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        DesignerAgentProperties properties = new DesignerAgentProperties();
        properties.setPlanMode(true);
        properties.setPlanModeSkipSimple(true);
        EditPlanApplicator applicator = new EditPlanApplicator(
                new AssistantBpmnToPlan(), new AssistantPlanCompiler(objectMapper));
        orchestrator = new DesignerAgentOrchestrator(
                properties,
                applicator,
                new PlanSkipEvaluator(properties),
                workflowValidator,
                objectMapper,
                planGenerator);
    }

    @Test
    void runTurn_emitsPlanReadyWhenComplexScenario() throws Exception {
        EditPlan plan = complexPlan();
        when(planGenerator.generate(
                eq("重构整流程加网关分支"),
                eq(MinimalBpmn),
                isNull(),
                isNull(),
                isNull(),
                any(DesignerAgentRun.class)))
                .thenReturn(new GenerateResult(plan, "将添加网关", "检索组件中"));

        List<AgentStreamEvent> events = new CopyOnWriteArrayList<>();
        DesignerAgentRun run = baseRun("重构整流程加网关分支", events);

        orchestrator.runTurn(run);

        assertEquals(AgentRunStage.AwaitPlan, run.getStage());
        assertTrue(events.stream().anyMatch(e -> "stage".equals(e.getType())));
        assertTrue(events.stream().anyMatch(e -> "thinking_delta".equals(e.getType())));
        assertTrue(events.stream().anyMatch(e -> "plan_ready".equals(e.getType())));
        assertTrue(events.stream().anyMatch(e -> "await_human".equals(e.getType())));
    }

    @Test
    void runTurn_skipsPlanGateForSimpleTwoOperations() throws Exception {
        EditPlan plan = twoOperationMetaPlan();
        when(planGenerator.generate(
                eq("更新节点名称"),
                eq(MinimalBpmn),
                isNull(),
                isNull(),
                isNull(),
                any(DesignerAgentRun.class)))
                .thenReturn(new GenerateResult(plan, "更新流程名", null));
        when(workflowValidator.validate(any())).thenReturn(passValidation());

        List<AgentStreamEvent> events = new CopyOnWriteArrayList<>();
        DesignerAgentRun run = baseRun("更新节点名称", events);

        orchestrator.runTurn(run);

        assertEquals(AgentRunStage.AwaitPreview, run.getStage());
        assertTrue(events.stream().anyMatch(e -> "preview_ready".equals(e.getType())));
        assertTrue(events.stream().noneMatch(e -> "plan_ready".equals(e.getType())));
    }

    @Test
    void processPlanConfirmation_emitsPreviewReadyAfterApply() throws Exception {
        when(workflowValidator.validate(any())).thenReturn(passValidation());

        List<AgentStreamEvent> events = new CopyOnWriteArrayList<>();
        DesignerAgentRun run = baseRun("加一个 HTTP 请求节点", events);
        run.setStage(AgentRunStage.AwaitPlan);
        run.setEditPlanJson(objectMapper.writeValueAsString(simpleAddNodePlan()));

        orchestrator.processPlanConfirmation(run, true, null);

        assertEquals(AgentRunStage.AwaitPreview, run.getStage());
        assertTrue(events.stream().anyMatch(e -> "validation".equals(e.getType())));
        assertTrue(events.stream().anyMatch(e -> "preview_ready".equals(e.getType())));
        assertNotNull(run.getCandidateXml());
        assertTrue(run.getCandidateXml().contains("Task_http"));
    }

    @Test
    void readOnlyScenario_emitsDoneWithoutEditPlan() {
        when(planGenerator.explainOnly(any(), any(), any())).thenReturn("这是一个测试流程。");

        List<AgentStreamEvent> events = new CopyOnWriteArrayList<>();
        DesignerAgentRun run = baseRun("解释一下这个流程干什么", events);

        orchestrator.runTurn(run);

        assertEquals(AgentRunStage.Done, run.getStage());
        assertTrue(events.stream().anyMatch(e -> "done".equals(e.getType())));
    }

    @Test
    void toolTraceContext_emitsToolStartAndEnd() {
        DesignerAgentRun run = new DesignerAgentRun();
        run.setRunId("run-1");
        List<AgentStreamEvent> events = new CopyOnWriteArrayList<>();
        run.setEventSink(events::add);

        DesignerAgentToolTraceContext.bind(run);
        DesignerAgentToolTraceContext.emitToolStart("bpmComp_aiPage", "{\"keyword\":\"http\"}");
        DesignerAgentToolTraceContext.emitToolEnd("bpmComp_aiPage", "json len=128");
        DesignerAgentToolTraceContext.clear();

        assertEquals(2, events.size());
        assertEquals("tool_start", events.get(0).getType());
        assertEquals("bpmComp_aiPage", events.get(0).getToolName());
        assertEquals("tool_end", events.get(1).getType());
        assertEquals(1, run.getToolStepCount());
    }

    private static DesignerAgentRun baseRun(String scenario, List<AgentStreamEvent> events) {
        DesignerAgentRun run = new DesignerAgentRun();
        run.setRunId("test-run");
        run.setUserScenario(scenario);
        run.setBaseBpmnXml(MinimalBpmn);
        run.setEventSink(events::add);
        return run;
    }

    private static AssistantWorkflowValidator.ValidationResult passValidation() {
        AssistantWorkflowValidator.ValidationResult result = new AssistantWorkflowValidator.ValidationResult();
        result.setIssues(List.of());
        result.setDispatchCode(AssistantVariables.DispatchPass);
        return result;
    }

    private static EditPlan twoOperationMetaPlan() {
        EditPlan plan = new EditPlan();
        plan.setProcessId("P1");
        EditOperation updateStart = new EditOperation();
        updateStart.setOp("updateNode");
        updateStart.setNodeId("Start_1");
        NodeSpec startPatch = new NodeSpec();
        startPatch.setName("开始");
        updateStart.setPatch(startPatch);
        EditOperation updateEnd = new EditOperation();
        updateEnd.setOp("updateNode");
        updateEnd.setNodeId("End_1");
        NodeSpec endPatch = new NodeSpec();
        endPatch.setName("结束");
        updateEnd.setPatch(endPatch);
        plan.setOperations(List.of(updateStart, updateEnd));
        return plan;
    }

    private static EditPlan simpleAddNodePlan() {
        EditPlan plan = new EditPlan();
        plan.setProcessId("P1");
        EditOperation removeFlow = new EditOperation();
        removeFlow.setOp("removeFlow");
        removeFlow.setFlowId("F1");
        EditOperation addNode = new EditOperation();
        addNode.setOp("addNode");
        NodeSpec node = new NodeSpec();
        node.setId("Task_http");
        node.setType("serviceTask");
        node.setName("HTTP");
        node.setComponentId("httpRequest");
        node.setParameters(Map.of("url", "https://example.com"));
        addNode.setNode(node);
        addNode.setAfterRef("Start_1");
        EditOperation addFlow = new EditOperation();
        addFlow.setOp("addFlow");
        FlowSpec flow = new FlowSpec();
        flow.setId("F2");
        flow.setSourceRef("Task_http");
        flow.setTargetRef("End_1");
        addFlow.setFlow(flow);
        plan.setOperations(List.of(removeFlow, addNode, addFlow));
        return plan;
    }

    private static EditPlan complexPlan() {
        EditPlan plan = new EditPlan();
        plan.setProcessId("P1");
        plan.setSummary("添加网关与分支");
        List<EditOperation> ops = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            EditOperation op = new EditOperation();
            op.setOp("addNode");
            NodeSpec node = new NodeSpec();
            node.setId("Task_" + i);
            node.setType("serviceTask");
            node.setComponentId("httpRequest");
            op.setNode(node);
            ops.add(op);
        }
        plan.setOperations(ops);
        return plan;
    }
}
