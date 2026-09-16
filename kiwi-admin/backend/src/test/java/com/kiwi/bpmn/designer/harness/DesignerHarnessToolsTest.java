package com.kiwi.bpmn.designer.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.service.BpmComponentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesignerHarnessToolsTest {

    @Mock
    BpmComponentService bpmComponentService;

    DesignerHarnessTools tools;

    @BeforeEach
    void setUp() {
        tools = new DesignerHarnessTools(
                bpmComponentService, null, new DesignerHarnessTurnContext(), new ObjectMapper());
    }

    @Test
    void search_and_get() {
        BpmComponent c = new BpmComponent();
        c.setId("classpath_httpRequest");
        c.setName("HTTP 请求");
        c.setGroup("网络");
        c.setDescription("发 HTTP");
        BpmComponentParameter p = new BpmComponentParameter();
        p.setKey("url");
        p.setRequired(true);
        p.setDescription("地址");
        c.setInputParameters(List.of(p));
        when(bpmComponentService.listCachedComponents()).thenReturn(List.of(c));
        when(bpmComponentService.fillComponentProperties(any())).thenReturn(c);
        when(bpmComponentService.resolveComponentById("classpath_httpRequest")).thenReturn(c);

        String search = tools.searchComponents("http");
        assertTrue(search.contains("classpath_httpRequest"));
        String detail = tools.getComponent("classpath_httpRequest");
        assertTrue(detail.contains("url"));
        assertTrue(detail.contains("required=true"));
    }
}