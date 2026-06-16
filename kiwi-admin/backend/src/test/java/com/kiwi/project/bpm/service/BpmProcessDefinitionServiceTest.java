package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.model.BpmProcess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmProcessDefinitionServiceTest {

    private static final String ProcessId = "p-test-001";

    private final BpmProcessDefinitionService service = new BpmProcessDefinitionService(null, null);

    @Test
    void syncBpmnIdentity_whenAlreadyAligned_returnsFalseAndKeepsXml() {
        String xml = alignedXml(ProcessId, "Demo");
        BpmProcess process = process(ProcessId, "Demo", xml);

        assertFalse(service.syncBpmnIdentity(process));
        assertEquals(xml, process.getBpmnXml());
    }

    @Test
    void syncBpmnIdentity_whenProcessIdMismatch_correctsProcessAndPlane() {
        BpmProcess process = process(ProcessId, "Demo", alignedXml("wrong-id", "Demo"));

        assertTrue(service.syncBpmnIdentity(process));
        assertTrue(process.getBpmnXml().contains("id=\"" + ProcessId + "\""));
        assertTrue(process.getBpmnXml().contains("bpmnElement=\"" + ProcessId + "\""));
    }

    @Test
    void syncBpmnIdentity_whenPlaneIdNotBpmnPlane1_correctsPlane() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  id="Definitions_1">
                  <bpmn:process id="wrong-id" name="Demo" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1"/>
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                    <bpmndi:BPMNPlane id="BPMNPlane_custom" bpmnElement="wrong-id"/>
                  </bpmndi:BPMNDiagram>
                </bpmn:definitions>
                """;
        BpmProcess process = process(ProcessId, "Demo", xml);

        assertTrue(service.syncBpmnIdentity(process));
        assertTrue(process.getBpmnXml().contains("bpmnElement=\"" + ProcessId + "\""));
    }

    @Test
    void syncBpmnIdentity_whenXmlInvalid_throws() {
        BpmProcess process = process(ProcessId, "Demo", "<not-xml");

        assertThrows(IllegalArgumentException.class, () -> service.syncBpmnIdentity(process));
    }

    private static BpmProcess process(String id, String name, String xml) {
        BpmProcess p = new BpmProcess();
        p.setId(id);
        p.setName(name);
        p.setBpmnXml(xml);
        return p;
    }

    private static String alignedXml(String processId, String name) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  id="Definitions_1">
                  <bpmn:process id="%s" name="%s" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1"/>
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="%s"/>
                  </bpmndi:BPMNDiagram>
                </bpmn:definitions>
                """.formatted(processId, name, processId);
    }
}
