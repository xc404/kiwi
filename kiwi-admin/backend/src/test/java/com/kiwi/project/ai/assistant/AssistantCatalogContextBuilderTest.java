package com.kiwi.project.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantCatalog;
import com.kiwi.bpmn.assistant.AssistantProperties;
import com.kiwi.bpmn.assistant.spi.AssistantBpmnLookup;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.service.BpmComponentPluginLoader;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmRemoteMarketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantCatalogContextBuilderTest {

    @Mock
    BpmComponentService componentService;
    @Mock
    com.kiwi.project.bpm.dao.BpmComponentDao componentDao;
    @Mock
    AssistantBpmnLookup bpmnLookup;
    @Mock
    BpmComponentPluginLoader pluginLoader;
    @Mock
    ObjectProvider<BpmRemoteMarketService> remoteProvider;

    private AssistantCatalogContextBuilder builder;

    @BeforeEach
    void setUp() {
        when(pluginLoader.buildPluginJarIndex()).thenReturn(Map.of());
        when(remoteProvider.getIfAvailable()).thenReturn(null);
        when(bpmnLookup.findMatureTemplates(anyString(), anyList(), anyInt())).thenReturn(List.of());
        builder = new AssistantCatalogContextBuilder(
                new AssistantProperties(),
                componentService,
                componentDao,
                bpmnLookup,
                pluginLoader,
                new ObjectMapper(),
                remoteProvider);
    }

    @Test
    void build_injectsDescriptionDelegateAndRequiredInputs() {
        BpmComponent component = new BpmComponent();
        component.setId("classpath_httpRequest");
        component.setKey("httpRequest");
        component.setName("HTTP 请求");
        component.setDescription("调用 HTTP API");
        component.setSource("classpath");

        BpmComponentParameter optional = parameter("method", false, "GET");
        BpmComponentParameter required = parameter("url", true, "https://example.test");
        component.setInputParameters(List.of(optional, required));
        when(componentDao.findAll()).thenReturn(List.of(component));
        when(componentService.fillComponentProperties(any(BpmComponent.class))).thenAnswer(i -> i.getArgument(0));

        AssistantCatalog catalog = builder.build("调用接口", List.of("HTTP"));

        assertEquals(1, catalog.getInstalled().size());
        AssistantCatalog.CatalogComponent entry = catalog.getInstalled().get(0);
        assertEquals("调用 HTTP API", entry.getDescription());
        assertEquals("${httpRequest}", entry.getDelegateExpression());
        assertEquals("url", entry.getInputs().get(0).getKey());
        assertTrue(entry.getInputs().get(0).isRequired());
        assertEquals("https://example.test", entry.getInputs().get(0).getExample());
    }

    @Test
    void build_attachesTopTemplateReferenceBpmn() {
        when(componentDao.findAll()).thenReturn(List.of());
        when(componentService.listAllComponents()).thenReturn(List.of());
        AssistantBpmnLookup.TemplateSummary summary = new AssistantBpmnLookup.TemplateSummary();
        summary.setPackId("pack-1");
        summary.setName("通知模板");
        summary.setReferenceProcessKey("notify");
        summary.setReferenceBpmnXml("<bpmn:definitions>reference</bpmn:definitions>");
        when(bpmnLookup.findMatureTemplates(anyString(), anyList(), anyInt())).thenReturn(List.of(summary));

        AssistantCatalog catalog = builder.build("发送通知", List.of("通知"));

        assertEquals(1, catalog.getTemplates().size());
        assertEquals("notify", catalog.getTemplates().get(0).getReferenceProcessKey());
        assertTrue(catalog.getTemplates().get(0).getReferenceBpmnXml().contains("reference"));
    }

    private BpmComponentParameter parameter(String key, boolean required, String example) {
        BpmComponentParameter parameter = new BpmComponentParameter();
        parameter.setKey(key);
        parameter.setRequired(required);
        parameter.setExample(example);
        return parameter;
    }
}
