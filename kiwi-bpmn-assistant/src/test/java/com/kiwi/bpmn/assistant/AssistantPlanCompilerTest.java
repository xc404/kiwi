package com.kiwi.bpmn.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.DefaultAssistantXmlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantPlanCompilerTest {

    private AssistantPlanCompiler compiler;
    private AssistantCatalog catalog;

    @BeforeEach
    void setUp() {
        compiler = new AssistantPlanCompiler(new ObjectMapper());
        catalog = new AssistantCatalog();
        AssistantCatalog.CatalogComponent component = new AssistantCatalog.CatalogComponent();
        component.setId("classpath_httpRequest");
        component.setDelegateExpression("${httpRequest}");
        component.setStatus("installed");
        catalog.setInstalled(List.of(component));
    }

    @Test
    void compile_generatesValidBpmnWithComponentParametersAndDi() {
        AssistantPlan plan = new AssistantPlan();
        plan.setProcessId("http_flow");
        plan.setName("调用接口");
        plan.setNodes(List.of(
                node("StartEvent_1", "startEvent", null, Map.of()),
                node("Activity_HTTP", "serviceTask", "classpath_httpRequest",
                        Map.of("url", "${requestUrl}", "method", "GET")),
                node("EndEvent_1", "endEvent", null, Map.of())));
        plan.setFlows(List.of(
                flow("Flow_1", "StartEvent_1", "Activity_HTTP"),
                flow("Flow_2", "Activity_HTTP", "EndEvent_1")));

        String xml = compiler.compile(plan, catalog);

        new DefaultAssistantXmlValidator().validate(xml);
        assertTrue(xml.contains("http://kiwi.com/bpmn"));
        assertTrue(xml.contains("kiwi:componentId=\"classpath_httpRequest\""));
        assertTrue(xml.contains("camunda:delegateExpression=\"${httpRequest}\""));
        assertTrue(xml.contains("<camunda:inputParameter name=\"url\">${requestUrl}</camunda:inputParameter>"));
        assertTrue(xml.contains("<bpmndi:BPMNShape"));
        assertTrue(xml.contains("<bpmndi:BPMNEdge"));
    }

    @Test
    void compile_allowsInstallableComponentForLaterInstallValidation() {
        AssistantCatalog.CatalogComponent shell = new AssistantCatalog.CatalogComponent();
        shell.setId("plugin_shell");
        shell.setStatus("available_to_install");
        shell.setRequiresInstall(true);
        catalog.setInstallable(List.of(shell));

        AssistantPlan plan = new AssistantPlan();
        plan.setProcessId("shell_flow");
        plan.setNodes(List.of(
                node("StartEvent_1", "startEvent", null, Map.of()),
                node("Activity_1", "serviceTask", "plugin_shell", Map.of("command", "echo hi")),
                node("EndEvent_1", "endEvent", null, Map.of())));
        plan.setFlows(List.of(
                flow("Flow_1", "StartEvent_1", "Activity_1"),
                flow("Flow_2", "Activity_1", "EndEvent_1")));

        String xml = compiler.compile(plan, catalog);

        assertTrue(xml.contains("kiwi:componentId=\"plugin_shell\""));
    }

    @Test
    void compileJson_rejectsUnknownWhenCatalogPresent() {
        String planJson = """
                {
                  "processId":"bad_flow",
                  "nodes":[
                    {"id":"StartEvent_1","type":"startEvent"},
                    {"id":"Activity_1","type":"serviceTask","componentId":"invented_component"},
                    {"id":"EndEvent_1","type":"endEvent"}
                  ],
                  "flows":[
                    {"id":"Flow_1","sourceRef":"StartEvent_1","targetRef":"Activity_1"},
                    {"id":"Flow_2","sourceRef":"Activity_1","targetRef":"EndEvent_1"}
                  ]
                }
                """;

        assertFalse(compiler.compile(planJson, new ObjectMapper().valueToTree(catalog).toString()).isPresent());
    }

    @Test
    void compile_withoutCatalog_allowsServiceTaskWithDefaultDelegate() {
        AssistantPlan plan = new AssistantPlan();
        plan.setProcessId("shell_flow");
        plan.setNodes(List.of(
                node("StartEvent_1", "startEvent", null, Map.of()),
                node("Activity_1", "serviceTask", "classpath_shell", Map.of("command", "echo hi")),
                node("EndEvent_1", "endEvent", null, Map.of())));
        plan.setFlows(List.of(
                flow("Flow_1", "StartEvent_1", "Activity_1"),
                flow("Flow_2", "Activity_1", "EndEvent_1")));

        String xml = compiler.compile(plan);
        assertTrue(xml.contains("kiwi:componentId=\"classpath_shell\""));
        assertTrue(xml.contains("${shell}"));
    }

    private AssistantPlan.Node node(
            String id, String type, String componentId, Map<String, Object> parameters) {
        AssistantPlan.Node node = new AssistantPlan.Node();
        node.setId(id);
        node.setType(type);
        node.setComponentId(componentId);
        node.setParameters(parameters);
        return node;
    }

    private AssistantPlan.Flow flow(String id, String sourceRef, String targetRef) {
        AssistantPlan.Flow flow = new AssistantPlan.Flow();
        flow.setId(id);
        flow.setSourceRef(sourceRef);
        flow.setTargetRef(targetRef);
        return flow;
    }
}
