package com.kiwi.project.ai.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.framework.session.SessionService;
import com.kiwi.project.ai.AiChatProperties;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.model.BpmTemplatePack;
import com.kiwi.project.bpm.model.BpmTemplateProcess;
import com.kiwi.project.bpm.service.BpmComponentPluginLoader;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmRemoteMarketService;
import com.kiwi.project.bpm.service.BpmTemplatePackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAuthoringCatalogContextBuilderTest {

    @Mock
    BpmComponentService componentService;
    @Mock
    com.kiwi.project.bpm.dao.BpmComponentDao componentDao;
    @Mock
    BpmTemplatePackService templatePackService;
    @Mock
    BpmComponentPluginLoader pluginLoader;
    @Mock
    SessionService sessionService;
    @Mock
    ObjectProvider<BpmRemoteMarketService> remoteProvider;

    private AiAuthoringCatalogContextBuilder builder;

    @BeforeEach
    void setUp() {
        when(pluginLoader.buildPluginJarIndex()).thenReturn(Map.of());
        when(remoteProvider.getIfAvailable()).thenReturn(null);
        builder = new AiAuthoringCatalogContextBuilder(
                new AiChatProperties(),
                componentService,
                componentDao,
                templatePackService,
                pluginLoader,
                sessionService,
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
        when(templatePackService.page(
                any(BpmTemplatePackService.PackQueryInput.class),
                any(Pageable.class),
                any())).thenReturn(new PageImpl<>(List.of()));

        AiAuthoringCatalog catalog = builder.build("调用接口", List.of("HTTP"));

        assertEquals(1, catalog.getInstalled().size());
        AiAuthoringCatalog.CatalogComponent entry = catalog.getInstalled().get(0);
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
        BpmTemplatePack pack = new BpmTemplatePack();
        pack.setId("pack-1");
        pack.setName("通知模板");
        when(templatePackService.page(
                any(BpmTemplatePackService.PackQueryInput.class),
                any(Pageable.class),
                any())).thenReturn(new PageImpl<>(List.of(pack)));
        BpmTemplateProcess process = new BpmTemplateProcess();
        process.setProcessKey("notify");
        process.setEntry(true);
        process.setBpmnXml("<bpmn:definitions>reference</bpmn:definitions>");
        when(templatePackService.listProcesses("pack-1", null)).thenReturn(List.of(process));

        AiAuthoringCatalog catalog = builder.build("发送通知", List.of("通知"));

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
