package com.kiwi.bpmn.designer.agent.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 需求澄清表单（Plan 生成前人机闸门）。 */
@Data
public class ClarificationForm {
    private List<ClarificationQuestion> questions = new ArrayList<>();
}
