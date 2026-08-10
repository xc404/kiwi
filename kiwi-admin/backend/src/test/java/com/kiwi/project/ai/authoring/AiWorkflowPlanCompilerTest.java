package com.kiwi.project.ai.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.system.ai.BpmDesignerXmlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiWorkflowPlanCompilerTest {

    private AiWorkflowPlanCompiler compiler;
    private AiAuthoringCatalog catalog;

    @BeforeEach
    void setUp() {
        compiler = new AiWorkflowPlanCompiler(new ObjectMapper());
        catalog = new AiAuthoringCatalog();
        AiAuthoringCatalog.CatalogComponent component = new AiAuthoringCatalog.CatalogComponent();
        component.setId("classpath_httpRequest");
        component.setDelegateExpression("${httpRequest}");
        component.setStatus("installed");
        catalog.setInstalled(List.of(component));
    }

    @Test
    void compile_generatesValidBpmnWithComponentParametersAndDi() {
        AiWorkflowPlan plan = new AiWorkflowPlan();
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

        new BpmDesignerXmlValidator().validate(xml);
        assertTrue(xml.contains("kiwi:componentId=\"classpath_httpRequest\""));
        assertTrue(xml.contains("camunda:delegateExpression=\"${httpRequest}\""));
        assertTrue(xml.contains("<camunda:inputParameter name=\"url\">${requestUrl}</camunda:inputParameter>"));
        assertTrue(xml.contains("<bpmndi:BPMNShape"));
        assertTrue(xml.contains("<bpmndi:BPMNEdge"));
    }

    @Test
    void compileJson_rejectsComponentOutsideInstalledCatalog() {
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

    private AiWorkflowPlan.Node node(
            String id, String type, String componentId, Map<String, Object> parameters) {
        AiWorkflowPlan.Node node = new AiWorkflowPlan.Node();
        node.setId(id);
        node.setType(type);
        node.setComponentId(componentId);
        node.setParameters(parameters);
        return node;
    }

    private AiWorkflowPlan.Flow flow(String id, String sourceRef, String targetRef) {
        AiWorkflowPlan.Flow flow = new AiWorkflowPlan.Flow();
        flow.setId(id);
        flow.setSourceRef(sourceRef);
        flow.setTargetRef(targetRef);
        return flow;
    }
}
