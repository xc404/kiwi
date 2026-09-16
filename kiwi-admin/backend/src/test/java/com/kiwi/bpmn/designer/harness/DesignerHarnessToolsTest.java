package com.kiwi.bpmn.designer.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.designer.harness.persist.DesignerHarnessSession;
import com.kiwi.project.bpm.KiwiBpmnXml;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmProcessIoAnalysisService;
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

    DesignerHarnessTurnContext turnContext;
    DesignerHarnessTools tools;

    @BeforeEach
    void setUp() {
        turnContext = new DesignerHarnessTurnContext();
        tools = new DesignerHarnessTools(
                bpmComponentService,
                null,
                turnContext,
                new ObjectMapper(),
                new BpmProcessIoAnalysisService(bpmComponentService));
    }

    @Test
    void search_and_get() {
        BpmComponent c = httpComponent();
        when(bpmComponentService.listCachedComponents()).thenReturn(List.of(c));
        when(bpmComponentService.fillComponentProperties(any())).thenReturn(c);
        when(bpmComponentService.resolveComponentById("classpath_httpRequest")).thenReturn(c);

        String search = tools.searchComponents("http");
        assertTrue(search.contains("classpath_httpRequest"));
        String detail = tools.getComponent("classpath_httpRequest");
        assertTrue(detail.contains("url"));
        assertTrue(detail.contains("required=true"));
    }

    @Test
    void listProcessIo_marksUnfilledStartVariable() {
        when(bpmComponentService.resolveComponentById("classpath_httpRequest")).thenReturn(httpComponent());
        DesignerHarnessSession session = new DesignerHarnessSession();
        session.setId("p1");
        turnContext.bind(session, """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  xmlns:kiwi="%s"
                                  id="Definitions_1" targetNamespace="tns">
                  <bpmn:process id="p1" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1"/>
                    <bpmn:serviceTask id="Activity_http" name="HTTP" kiwi:componentId="classpath_httpRequest">
                      <bpmn:extensionElements>
                        <camunda:inputOutput>
                          <camunda:inputParameter name="url">${requestUrl}</camunda:inputParameter>
                        </camunda:inputOutput>
                      </bpmn:extensionElements>
                    </bpmn:serviceTask>
                    <bpmn:endEvent id="EndEvent_1"/>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_http"/>
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_http" targetRef="EndEvent_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(KiwiBpmnXml.Namespace));
        try {
            String text = tools.listProcessIo();
            assertTrue(text.contains("Activity_http"));
            assertTrue(text.contains("in url"));
            assertTrue(text.contains("kind=Expression"));
            assertTrue(text.contains("requestUrl"));
            assertTrue(text.contains("neededBy=Activity_http.url"));
        } finally {
            turnContext.clear();
        }
    }

    private BpmComponent httpComponent() {
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
        return c;
    }
}
