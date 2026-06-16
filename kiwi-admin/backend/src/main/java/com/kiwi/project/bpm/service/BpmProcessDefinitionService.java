package com.kiwi.project.bpm.service;

import cn.hutool.core.lang.UUID;
import com.kiwi.framework.session.SessionService;
import com.kiwi.project.bpm.model.BpmProcess;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class BpmProcessDefinitionService implements InitializingBean
{
    private static final String BpmnNs = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String BpmnDiNs = "http://www.omg.org/spec/BPMN/20100524/DI";

    public static final String BpmnIdentityCorrectedMsg =
            "已自动将 BPMN 中的流程 ID 与库内 id 对齐（process / BPMNPlane）";

    @Value("${xbpm.process-definition.template-path:classpath:bpm/bpm-template.xml}")
    private String processDefinitionTemplatePath;
    private String processDefinitionTemplate;
    public static final String XBPM = "xbpm";
    private final SessionService sessionService;
    private final ResourceLoader resourceLoader;

    public String getInitProcessDefinitionXml(String id, String processDefinitionName) {
        if( processDefinitionTemplate != null ) {
            return processDefinitionTemplate.replace("${definition_id}", id).replace("${definition_name}", processDefinitionName);
        }
        return null;
    }


    @Override
    public void afterPropertiesSet() throws Exception {

        var resource = resourceLoader.getResource(processDefinitionTemplatePath);
        try (InputStream in = resource.getInputStream()) {
            this.processDefinitionTemplate = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }

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
        syncBpmnIdentity(bpmProcess);
        return bpmProcess;
    }

    /**
     * 将 BPMN 中 {@code process@id} 与全部 {@code BPMNPlane@bpmnElement} 对齐为 {@link BpmProcess#getId()}；
     * 若 {@link BpmProcess#getName()} 非空则同步 {@code process@name}。
     *
     * @return 修正前存在 id / bpmnElement 与库内 id 不一致时为 {@code true}
     */
    public boolean syncBpmnIdentity(BpmProcess bpmProcess) {
        if( bpmProcess == null || StringUtils.isBlank(bpmProcess.getId()) ) {
            throw new IllegalArgumentException("流程 id 不能为空");
        }
        String xml = bpmProcess.getBpmnXml();
        if( StringUtils.isBlank(xml) ) {
            return false;
        }
        String expectedId = bpmProcess.getId().trim();

        Document doc = parseBpmnDocument(xml);
        Element processEl = findFirstProcessElement(doc);
        if( processEl == null ) {
            throw new IllegalArgumentException("BPMN XML 中未找到 process 元素");
        }

        boolean identityCorrected = false;
        if( !expectedId.equals(processEl.getAttribute("id")) ) {
            identityCorrected = true;
            processEl.setAttribute("id", expectedId);
        }

        boolean xmlMutated = identityCorrected;
        if( StringUtils.isNotBlank(bpmProcess.getName()) ) {
            String currentName = processEl.getAttribute("name");
            if( !bpmProcess.getName().equals(currentName) ) {
                processEl.setAttribute("name", bpmProcess.getName());
                xmlMutated = true;
            }
        }

        NodeList planes = doc.getElementsByTagNameNS(BpmnDiNs, "BPMNPlane");
        for( int i = 0; i < planes.getLength(); i++ ) {
            Element plane = (Element) planes.item(i);
            if( !expectedId.equals(plane.getAttribute("bpmnElement")) ) {
                identityCorrected = true;
                xmlMutated = true;
                plane.setAttribute("bpmnElement", expectedId);
            }
        }

        if( xmlMutated ) {
            bpmProcess.setBpmnXml(serializeDocument(doc));
        }
        return identityCorrected;
    }

    private Document parseBpmnDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch( Exception e ) {
            throw new IllegalArgumentException("无法解析 BPMN XML: " + e.getMessage(), e);
        }
    }

    private Element findFirstProcessElement(Document doc) {
        NodeList processes = doc.getElementsByTagNameNS(BpmnNs, "process");
        if( processes.getLength() > 0 ) {
            return (Element) processes.item(0);
        }
        processes = doc.getElementsByTagName("process");
        if( processes.getLength() > 0 ) {
            return (Element) processes.item(0);
        }
        return null;
    }

    private String serializeDocument(Document doc) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch( Exception e ) {
            throw new IllegalArgumentException("无法序列化 BPMN XML: " + e.getMessage(), e);
        }
    }

    public String getNewProcessId() {
        return "p-" + UUID.fastUUID();
    }


}
