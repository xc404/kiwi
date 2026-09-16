package com.kiwi.bpmn.designer.harness.bpmn;

import com.kiwi.bpmn.designer.harness.model.EditOperation;
import com.kiwi.bpmn.designer.harness.model.NodeSpec;
import com.kiwi.project.ai.authoring.AiAuthoringCatalog;
import com.kiwi.project.ai.authoring.AiWorkflowPlan;
import com.kiwi.project.ai.authoring.AiWorkflowPlanCompiler;
import com.kiwi.project.bpm.KiwiBpmnXml;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignerHarnessBpmnRoundTripTest {

    @Test
    void read_simpleProcess() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:kiwi="%s"
                                  id="Definitions_1" targetNamespace="tns">
                  <bpmn:process id="p1" name="demo" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1"/>
                    <bpmn:serviceTask id="Activity_1" name="HTTP" kiwi:componentId="classpath_httpRequest"/>
                    <bpmn:endEvent id="EndEvent_1"/>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_1"/>
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_1" targetRef="EndEvent_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(KiwiBpmnXml.Namespace);
        DesignerHarnessBpmnReader.ParseResult parsed = new DesignerHarnessBpmnReader().read(xml);
        assertEquals("p1", parsed.plan.getProcessId());
        assertEquals(3, parsed.plan.getNodes().size());
        assertEquals("classpath_httpRequest", parsed.plan.getNodes().get(1).getComponentId());
        assertEquals(2, parsed.plan.getFlows().size());
    }

    @Test
    void read_componentId_fromCamundaProperty() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  xmlns:kiwi="%s"
                                  id="Definitions_1" targetNamespace="tns">
                  <bpmn:process id="p1" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1"/>
                    <bpmn:serviceTask id="Activity_1" name="Shell">
                      <bpmn:extensionElements>
                        <camunda:properties>
                          <camunda:property name="componentId" value="plugin_shell"/>
                        </camunda:properties>
                      </bpmn:extensionElements>
                    </bpmn:serviceTask>
                    <bpmn:endEvent id="EndEvent_1"/>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_1"/>
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_1" targetRef="EndEvent_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(KiwiBpmnXml.Namespace);
        DesignerHarnessBpmnReader.ParseResult parsed = new DesignerHarnessBpmnReader().read(xml);
        assertEquals("plugin_shell", parsed.plan.getNodes().get(1).getComponentId());
    }

    @Test
    void addNode_afterStart() {
        AiWorkflowPlan plan = new AiWorkflowPlan();
        AiWorkflowPlan.Node start = new AiWorkflowPlan.Node();
        start.setId("StartEvent_1");
        start.setType("startEvent");
        AiWorkflowPlan.Node end = new AiWorkflowPlan.Node();
        end.setId("EndEvent_1");
        end.setType("endEvent");
        plan.getNodes().add(start);
        plan.getNodes().add(end);
        AiWorkflowPlan.Flow flow = new AiWorkflowPlan.Flow();
        flow.setId("Flow_1");
        flow.setSourceRef("StartEvent_1");
        flow.setTargetRef("EndEvent_1");
        plan.getFlows().add(flow);

        EditOperation op = new EditOperation();
        op.setOp("addNode");
        op.setAfterRef("StartEvent_1");
        NodeSpec node = new NodeSpec();
        node.setId("Activity_del");
        node.setType("serviceTask");
        node.setName("删除");
        node.setComponentId("classpath_httpRequest");
        op.setNode(node);

        new DesignerHarnessPlanMutator().apply(plan, List.of(op));
        assertEquals(List.of("StartEvent_1", "Activity_del", "EndEvent_1"),
                plan.getNodes().stream().map(AiWorkflowPlan.Node::getId).toList());
        assertTrue(plan.getFlows().stream().anyMatch(f ->
                "StartEvent_1".equals(f.getSourceRef()) && "Activity_del".equals(f.getTargetRef())));
        assertTrue(plan.getFlows().stream().anyMatch(f ->
                "Activity_del".equals(f.getSourceRef()) && "EndEvent_1".equals(f.getTargetRef())));
        assertTrue(plan.getFlows().stream().noneMatch(f ->
                "StartEvent_1".equals(f.getSourceRef()) && "EndEvent_1".equals(f.getTargetRef())));
    }

    @Test
    void compile_insertedServiceTask() {
        AiWorkflowPlan plan = new AiWorkflowPlan();
        plan.setProcessId("p1");
        AiWorkflowPlan.Node start = new AiWorkflowPlan.Node();
        start.setId("StartEvent_1");
        start.setType("startEvent");
        AiWorkflowPlan.Node task = new AiWorkflowPlan.Node();
        task.setId("Activity_del");
        task.setType("serviceTask");
        task.setName("删除");
        task.setComponentId("classpath_httpRequest");
        AiWorkflowPlan.Node end = new AiWorkflowPlan.Node();
        end.setId("EndEvent_1");
        end.setType("endEvent");
        plan.getNodes().addAll(List.of(start, end, task));
        AiWorkflowPlan.Flow f1 = new AiWorkflowPlan.Flow();
        f1.setId("Flow_1");
        f1.setSourceRef("StartEvent_1");
        f1.setTargetRef("Activity_del");
        AiWorkflowPlan.Flow f2 = new AiWorkflowPlan.Flow();
        f2.setId("Flow_2");
        f2.setSourceRef("Activity_del");
        f2.setTargetRef("EndEvent_1");
        plan.getFlows().addAll(List.of(f1, f2));

        AiAuthoringCatalog catalog = new AiAuthoringCatalog();
        AiAuthoringCatalog.CatalogComponent component = new AiAuthoringCatalog.CatalogComponent();
        component.setId("classpath_httpRequest");
        component.setDelegateExpression("${httpRequest}");
        catalog.getInstalled().add(component);

        String xml = new AiWorkflowPlanCompiler(new ObjectMapper()).compile(plan, catalog);
        assertTrue(xml.contains("xmlns:kiwi=\"" + KiwiBpmnXml.Namespace + "\""));
        assertTrue(xml.contains("Activity_del"));
        assertTrue(xml.contains("kiwi:componentId=\"classpath_httpRequest\""));
        assertTrue(xml.contains("<camunda:property name=\"componentId\" value=\"classpath_httpRequest\"/>"));
        assertTrue(boundsX(xml, "StartEvent_1") < boundsX(xml, "Activity_del"));
        assertTrue(boundsX(xml, "Activity_del") < boundsX(xml, "EndEvent_1"));
    }

    private int boundsX(String xml, String nodeId) {
        Pattern pattern = Pattern.compile(
                "bpmnElement=\"" + Pattern.quote(nodeId) + "\"[\\s\\S]*?<dc:Bounds x=\"(\\d+)\"");
        Matcher matcher = pattern.matcher(xml);
        assertTrue(matcher.find(), "缺少 " + nodeId + " 的 DI Bounds");
        return Integer.parseInt(matcher.group(1));
    }

    @Test
    void read_subprocess_warns() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  id="Definitions_1" targetNamespace="tns">
                  <bpmn:process id="p1" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1"/>
                    <bpmn:subProcess id="Sub_1"/>
                    <bpmn:endEvent id="EndEvent_1"/>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Sub_1"/>
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Sub_1" targetRef="EndEvent_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
        DesignerHarnessBpmnReader.ParseResult parsed = new DesignerHarnessBpmnReader().read(xml);
        assertTrue(parsed.warnings.stream().anyMatch(w -> w.contains("subProcess")));
    }
}