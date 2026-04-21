package com.kiwi.project.ai.bpm;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * BPMN XML 最小校验：长度上限 + 可解析为 XML + 根元素为 definitions（BPMN 2.0）。
 */
@Component
public class BpmDesignerXmlValidator {

    public static final int MAX_XML_CHARS = 512_000;

    public void validate(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("BPMN XML 不能为空");
        }
        if (xml.length() > MAX_XML_CHARS) {
            throw new IllegalArgumentException("BPMN XML 超过最大长度 " + MAX_XML_CHARS);
        }
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        try {
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setNamespaceAware(true);
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            String local = doc.getDocumentElement().getLocalName();
            if (local == null || !"definitions".equals(local)) {
                throw new IllegalArgumentException("BPMN 根元素须为 bpmn:definitions");
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("BPMN XML 解析失败：" + e.getMessage());
        }
    }
}
