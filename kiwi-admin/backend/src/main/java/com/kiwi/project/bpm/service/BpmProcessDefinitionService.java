package com.kiwi.project.bpm.service;

import cn.hutool.core.lang.UUID;
import com.kiwi.framework.session.SessionService;
import com.kiwi.project.bpm.model.BpmProcess;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class BpmProcessDefinitionService implements InitializingBean
{
    @Value("${xbpm.process-definition.template-path:classpath:bpm/bpm-template.xml}")
    private String processDefinitionTemplatePath;
    private String processDefinitionTemplate;
    public static final String XBPM = "xbpm";
    private final SessionService sessionService;

    public String getInitProcessDefinitionXml(String id, String processDefinitionName) {
        if( processDefinitionTemplate != null ) {
            return processDefinitionTemplate.replace("${definition_id}", id).replace("${definition_name}", processDefinitionName);
        }
        return null;
    }


    @Override
    public void afterPropertiesSet() throws Exception {

        File file = ResourceUtils.getFile(processDefinitionTemplatePath);
        this.processDefinitionTemplate = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

    }

    public BpmProcess createProcessDefinition(String name) {
        BpmProcess bpmProcess = new BpmProcess();
        String id = getNewProcessId();
        bpmProcess.setName(name);
        bpmProcess.setId(id);
        String xml = this.processDefinitionTemplate;
        bpmProcess.setBpmnXml(xml);
        bpmProcess.setCreatedBy(sessionService.getCurrentUser().getId());
        bpmProcess.setCreatedTime(new Date());
        updateIdAndName(bpmProcess);
        return bpmProcess;
    }

    public static void updateIdAndName(BpmProcess bpmProcess) {
        String processReplace = "<bpmn:process id=\"${definition_id}\" isExecutable=\"true\" name=\"${definition_name}\">"
                .replace("${definition_id}", bpmProcess.getId()).replace("${definition_name}", bpmProcess.getName());
        String xml = bpmProcess.getBpmnXml().replaceAll("<bpmn:process.*\">", processReplace);
        String BPMNPlaneReplace = "<bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"${definition_id}\">"
                .replace("${definition_id}", bpmProcess.getId());
        xml = xml.replaceAll("<bpmndi:BPMNPlane id=\"BPMNPlane_1\".*\">", BPMNPlaneReplace);
        bpmProcess.setBpmnXml(xml);
    }

    public static String getNewProcessId() {
        return "p-" + UUID.fastUUID();
    }


}
