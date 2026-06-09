package com.kiwi.project.bpm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "流程定义 BPMN XML")
public class BpmProcessDefinitionXmlDto {

    @Schema(description = "BPMN 2.0 XML 文本")
    private String bpmn20Xml;
}
