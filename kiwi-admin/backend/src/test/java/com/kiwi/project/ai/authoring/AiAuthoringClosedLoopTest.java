package com.kiwi.project.ai.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.ai.AiChatProperties;
import com.kiwi.project.ai.authoring.delegate.AiAuthoringInstallDelegate;
import com.kiwi.project.ai.authoring.delegate.AiAuthoringMarkPreviewDelegate;
import com.kiwi.project.ai.authoring.delegate.AiAuthoringSaveDelegate;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.dto.BpmRemoteMarketInstallResultDto;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmComponentPluginLoader;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import com.kiwi.project.bpm.service.BpmRemoteMarketInstallService;
import com.kiwi.project.bpm.service.BpmTemplatePackManifestScanner;
import com.kiwi.project.system.ai.BpmDesignerXmlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.operaton.bpm.engine.delegate.DelegateExecution;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAuthoringClosedLoopTest {

    @Mock
    BpmComponentService componentService;
    @Mock
    BpmComponentPluginLoader pluginLoader;
    @Mock
    BpmRemoteMarketInstallService installService;
    @Mock
    BpmProcessDefinitionDao processDao;
    @Mock
    BpmProcessDefinitionService processDefinitionService;
    @Mock
    DelegateExecution execution;

    private ObjectMapper objectMapper;
    private BpmAiWorkflowValidator validator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AiAuthoringRuleSet rules = new AiAuthoringRuleSet(objectMapper);
        rules.init();
        when(pluginLoader.buildPluginJarIndex()).thenReturn(Map.of());
        validator = new BpmAiWorkflowValidator(
                new BpmDesignerXmlValidator(),
                componentService,
                pluginLoader,
                new BpmTemplatePackManifestScanner(new BpmDesignerXmlValidator()),
                new AiChatProperties(),
                objectMapper,
                rules);
    }

    @Test
    void invalidCandidate_repairCompile_previewAndSave() {
        String invalid = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  id="Definitions_1" targetNamespace="tns">
                  <bpmn:process id="p1"><bpmn:startEvent id="StartEvent_1"/></bpmn:process>
                </bpmn:definitions>
                """;
        assertEquals(AiAuthoringVariables.DispatchRepair,
                validator.validate(invalid, new AiAuthoringCatalog()).getDispatchCode());

        AiWorkflowPlan plan = new AiWorkflowPlan();
        plan.setProcessId("repaired_flow");
        plan.setNodes(List.of(
                node("StartEvent_1", "startEvent"),
                node("UserTask_1", "userTask"),
                node("EndEvent_1", "endEvent")));
        plan.setFlows(List.of(
                flow("Flow_1", "StartEvent_1", "UserTask_1"),
                flow("Flow_2", "UserTask_1", "EndEvent_1")));
        String repaired = new AiWorkflowPlanCompiler(objectMapper).compile(plan, new AiAuthoringCatalog());
        assertEquals(AiAuthoringVariables.DispatchPass,
                validator.validate(repaired, new AiAuthoringCatalog()).getDispatchCode());

        new AiAuthoringMarkPreviewDelegate().execute(execution);
        BpmProcess target = new BpmProcess();
        target.setId("target-1");
        when(execution.getVariable(AiAuthoringVariables.PreviewConfirmed)).thenReturn(true);
        when(execution.getVariable(AiAuthoringVariables.TargetProcessId)).thenReturn("target-1");
        when(execution.getVariable(AiAuthoringVariables.CandidateXml)).thenReturn(repaired);
        when(processDao.findById("target-1")).thenReturn(Optional.of(target));

        new AiAuthoringSaveDelegate(processDao, processDefinitionService).execute(execution);

        assertEquals(repaired, target.getBpmnXml());
        verify(processDao).save(target);
        verify(execution).setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageDone);
    }

    @Test
    void missingPlugin_confirmInstall_revalidateAndPreview() throws Exception {
        String componentId = "plugin_slackNotify";
        String candidate = componentXml(componentId);
        AiAuthoringCatalog catalog = installableCatalog(componentId);
        assertEquals(AiAuthoringVariables.DispatchInstall,
                validator.validate(candidate, catalog).getDispatchCode());

        when(execution.getVariable(AiAuthoringVariables.InstallAccepted)).thenReturn(true);
        when(execution.getVariable(AiAuthoringVariables.PluginHintJson)).thenReturn(
                "{\"componentId\":\"plugin_slackNotify\",\"severity\":\"INSTALL\"}");
        when(execution.getVariable(AiAuthoringVariables.CatalogJson))
                .thenReturn(objectMapper.writeValueAsString(catalog));
        when(execution.getVariable(AiAuthoringVariables.InitiatorUserId)).thenReturn("user-1");
        BpmRemoteMarketInstallResultDto installResult = new BpmRemoteMarketInstallResultDto();
        installResult.setInstalledComponentKeys(List.of(componentId));
        when(installService.installPlugin("slack-plugin", "1.0.0", "official", "user-1"))
                .thenReturn(installResult);

        new AiAuthoringInstallDelegate(installService, objectMapper).execute(execution);

        ArgumentCaptor<Object> updatedCatalogCaptor = ArgumentCaptor.forClass(Object.class);
        verify(execution).setVariable(eq(AiAuthoringVariables.CatalogJson), updatedCatalogCaptor.capture());
        AiAuthoringCatalog updatedCatalog = objectMapper.readValue(
                String.valueOf(updatedCatalogCaptor.getValue()), AiAuthoringCatalog.class);
        BpmComponent installedComponent = new BpmComponent();
        installedComponent.setId(componentId);
        when(componentService.resolveComponentById(componentId)).thenReturn(installedComponent);
        assertEquals(AiAuthoringVariables.DispatchPass,
                validator.validate(candidate, updatedCatalog).getDispatchCode());

        new AiAuthoringMarkPreviewDelegate().execute(execution);
        verify(execution).setVariable(
                AiAuthoringVariables.Stage, AiAuthoringVariables.StageAwaitPreview);
    }

    private AiWorkflowPlan.Node node(String id, String type) {
        AiWorkflowPlan.Node node = new AiWorkflowPlan.Node();
        node.setId(id);
        node.setType(type);
        return node;
    }

    private AiWorkflowPlan.Flow flow(String id, String source, String target) {
        AiWorkflowPlan.Flow flow = new AiWorkflowPlan.Flow();
        flow.setId(id);
        flow.setSourceRef(source);
        flow.setTargetRef(target);
        return flow;
    }

    private AiAuthoringCatalog installableCatalog(String componentId) {
        AiAuthoringCatalog catalog = new AiAuthoringCatalog();
        AiAuthoringCatalog.CatalogComponent component = new AiAuthoringCatalog.CatalogComponent();
        component.setId(componentId);
        component.setStatus("available_to_install");
        component.setRequiresInstall(true);
        component.setMarketSlug("slack-plugin");
        component.setMarketVersion("1.0.0");
        component.setMarketSourceId("official");
        catalog.setInstallable(List.of(component));
        return catalog;
    }

    private String componentXml(String componentId) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:kiwi="http://kiwi.io/schema/bpmn"
                                  id="Definitions_1" targetNamespace="tns">
                  <bpmn:process id="p1" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1"/>
                    <bpmn:serviceTask id="Activity_1" kiwi:componentId="%s"/>
                    <bpmn:endEvent id="EndEvent_1"/>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_1"/>
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_1" targetRef="EndEvent_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(componentId);
    }
}
