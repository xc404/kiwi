package com.kiwi.bpmn.component.slack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlackNotifyActivityTest {

    private final SlackNotifyActivity activity = new SlackNotifyActivity();

    @Test
    void buildPayload_textOnly() {
        assertEquals("{\"text\":\"hello\"}", activity.buildPayload("hello", null));
    }

    @Test
    void buildPayload_withChannel_escapesQuotes() {
        String json = activity.buildPayload("say \"hi\"", "#general");
        assertTrue(json.contains("\"channel\":\"#general\""));
        assertTrue(json.contains("say \\\"hi\\\""));
    }
}
