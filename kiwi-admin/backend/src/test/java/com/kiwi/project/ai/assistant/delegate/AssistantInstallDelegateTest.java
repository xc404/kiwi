package com.kiwi.project.ai.assistant.delegate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.project.bpm.service.BpmRemoteMarketInstallService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.operaton.bpm.engine.delegate.DelegateExecution;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantInstallDelegateTest {

    @Mock
    BpmRemoteMarketInstallService installService;
    @Mock
    DelegateExecution execution;

    @Test
    void execute_confirmedInstallsPluginFromCatalogMetadata() throws Exception {
        when(execution.getVariable(AssistantVariables.InstallAccepted)).thenReturn(true);
        when(execution.getVariable(AssistantVariables.PluginHintJson)).thenReturn(
                "{\"componentId\":\"plugin_slackNotify\",\"severity\":\"INSTALL\"}");
        when(execution.getVariable(AssistantVariables.CatalogJson)).thenReturn("""
                {
                  "installable":[{
                    "id":"plugin_slackNotify",
                    "marketSlug":"slack-plugin",
                    "marketVersion":"1.2.0",
                    "marketSourceId":"official",
                    "status":"available_to_install",
                    "requiresInstall":true
                  }]
                }
                """);
        when(execution.getVariable(AssistantVariables.InitiatorUserId)).thenReturn("user-1");
        AssistantInstallDelegate delegate =
                new AssistantInstallDelegate(installService, new ObjectMapper());

        delegate.execute(execution);

        verify(installService).installPlugin("slack-plugin", "1.2.0", "official", "user-1");
        verify(execution).setVariable(AssistantVariables.Stage, AssistantVariables.StageInstall);
        ArgumentCaptor<Object> catalogCaptor = ArgumentCaptor.forClass(Object.class);
        verify(execution).setVariable(eq(AssistantVariables.CatalogJson), catalogCaptor.capture());
        String updatedCatalog = String.valueOf(catalogCaptor.getValue());
        assertTrue(updatedCatalog.contains("\"status\":\"installed\""));
        assertTrue(updatedCatalog.contains("\"requiresInstall\":false"));
        verify(execution).setVariable(AssistantVariables.PluginHintJson, null);
    }
}
