package com.kiwi.bpmn.assistant;

import com.kiwi.bpmn.assistant.spi.AssistantXmlValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * 默认 XML 校验：长度上限 + well-formed + 根元素 definitions。
 */
@Component
@ConditionalOnMissingBean(AssistantXmlValidator.class)
public class DefaultAssistantXmlValidator implements AssistantXmlValidator {

    public static final int MaxXmlChars = 512_000;

    @Override
    public void validate(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("BPMN XML 不能为空");
        }
        if (xml.length() > MaxXmlChars) {
            throw new IllegalArgumentException("BPMN XML 超过最大长度 " + MaxXmlChars);
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
