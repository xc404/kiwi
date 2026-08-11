package com.kiwi.project.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantCatalog;
import com.kiwi.bpmn.assistant.AssistantPlan;
import com.kiwi.bpmn.assistant.AssistantPlanCompiler;
import com.kiwi.bpmn.assistant.AssistantProperties;
import com.kiwi.bpmn.assistant.AssistantRuleSet;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.bpmn.assistant.AssistantWorkflowValidator;
import com.kiwi.bpmn.assistant.DefaultAssistantXmlValidator;
import com.kiwi.project.ai.assistant.delegate.AssistantMarkPreviewDelegate;
import com.kiwi.bpmn.assistant.spi.AssistantComponentLookup;
import com.kiwi.project.ai.assistant.delegate.AssistantInstallDelegate;
import com.kiwi.project.ai.assistant.delegate.AssistantSaveDelegate;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.dto.BpmRemoteMarketInstallResultDto;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import com.kiwi.project.bpm.service.BpmRemoteMarketInstallService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.operaton.bpm.engine.delegate.DelegateExecution;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantClosedLoopTest {

    @Mock
    AssistantComponentLookup componentLookup;
    @Mock
    BpmRemoteMarketInstallService installService;
    @Mock
    BpmProcessDefinitionDao processDao;
    @Mock
    BpmProcessDefinitionService processDefinitionService;
    @Mock
    DelegateExecution execution;

    private ObjectMapper objectMapper;
    private AssistantWorkflowValidator validator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AssistantRuleSet rules = new AssistantRuleSet(objectMapper);
        rules.init();
        org.mockito.Mockito.lenient().when(componentLookup.exists(anyString())).thenReturn(false);
        org.mockito.Mockito.lenient().when(componentLookup.requiredInputKeys(anyString())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(componentLookup.pluginMissingHint(anyString())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(componentLookup.resolveDelegateExpression(anyString()))
                .thenReturn(Optional.empty());
        validator = new AssistantWorkflowValidator(
                new DefaultAssistantXmlValidator(),
                componentLookup,
                new AssistantProperties(),
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
        assertEquals(AssistantVariables.DispatchRepair,
                validator.validate(invalid, new AssistantCatalog()).getDispatchCode());

        AssistantPlan plan = new AssistantPlan();
        plan.setProcessId("repaired_flow");
        plan.setNodes(List.of(
                node("StartEvent_1", "startEvent"),
                node("UserTask_1", "userTask"),
                node("EndEvent_1", "endEvent")));
        plan.setFlows(List.of(
                flow("Flow_1", "StartEvent_1", "UserTask_1"),
                flow("Flow_2", "UserTask_1", "EndEvent_1")));
        String repaired = new AssistantPlanCompiler(objectMapper).compile(plan, new AssistantCatalog());
        assertEquals(AssistantVariables.DispatchPass,
                validator.validate(repaired, new AssistantCatalog()).getDispatchCode());

        new AssistantMarkPreviewDelegate().execute(execution);
        BpmProcess target = new BpmProcess();
        target.setId("target-1");
        when(execution.getVariable(AssistantVariables.PreviewConfirmed)).thenReturn(true);
        when(execution.getVariable(AssistantVariables.TargetProcessId)).thenReturn("target-1");
        when(execution.getVariable(AssistantVariables.CandidateXml)).thenReturn(repaired);
        when(processDao.findById("target-1")).thenReturn(Optional.of(target));

        new AssistantSaveDelegate(processDao, processDefinitionService).execute(execution);

        assertEquals(repaired, target.getBpmnXml());
        verify(processDao).save(target);
        verify(execution).setVariable(AssistantVariables.Stage, AssistantVariables.StageDone);
    }

    @Test
    void missingPlugin_confirmInstall_revalidateAndPreview() throws Exception {
        String componentId = "plugin_slackNotify";
        String candidate = componentXml(componentId);
        AssistantCatalog catalog = installableCatalog(componentId);
        assertEquals(AssistantVariables.DispatchInstall,
                validator.validate(candidate, catalog).getDispatchCode());

        when(execution.getVariable(AssistantVariables.InstallAccepted)).thenReturn(true);
        when(execution.getVariable(AssistantVariables.PluginHintJson)).thenReturn(
                "{\"componentId\":\"plugin_slackNotify\",\"severity\":\"INSTALL\"}");
        when(execution.getVariable(AssistantVariables.CatalogJson))
                .thenReturn(objectMapper.writeValueAsString(catalog));
        when(execution.getVariable(AssistantVariables.InitiatorUserId)).thenReturn("user-1");
        BpmRemoteMarketInstallResultDto installResult = new BpmRemoteMarketInstallResultDto();
        installResult.setInstalledComponentKeys(List.of(componentId));
        when(installService.installPlugin("slack-plugin", "1.0.0", "official", "user-1"))
                .thenReturn(installResult);

        new AssistantInstallDelegate(installService, objectMapper).execute(execution);

        ArgumentCaptor<Object> updatedCatalogCaptor = ArgumentCaptor.forClass(Object.class);
        verify(execution).setVariable(eq(AssistantVariables.CatalogJson), updatedCatalogCaptor.capture());
        AssistantCatalog updatedCatalog = objectMapper.readValue(
                String.valueOf(updatedCatalogCaptor.getValue()), AssistantCatalog.class);
        when(componentLookup.exists(componentId)).thenReturn(true);
        assertEquals(AssistantVariables.DispatchPass,
                validator.validate(candidate, updatedCatalog).getDispatchCode());

        new AssistantMarkPreviewDelegate().execute(execution);
        verify(execution).setVariable(
                AssistantVariables.Stage, AssistantVariables.StageAwaitPreview);
    }

    private AssistantPlan.Node node(String id, String type) {
        AssistantPlan.Node node = new AssistantPlan.Node();
        node.setId(id);
        node.setType(type);
        return node;
    }

    private AssistantPlan.Flow flow(String id, String source, String target) {
        AssistantPlan.Flow flow = new AssistantPlan.Flow();
        flow.setId(id);
        flow.setSourceRef(source);
        flow.setTargetRef(target);
        return flow;
    }

    private AssistantCatalog installableCatalog(String componentId) {
        AssistantCatalog catalog = new AssistantCatalog();
        AssistantCatalog.CatalogComponent component = new AssistantCatalog.CatalogComponent();
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
                                  xmlns:kiwi="http://kiwi.com/bpmn"
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
