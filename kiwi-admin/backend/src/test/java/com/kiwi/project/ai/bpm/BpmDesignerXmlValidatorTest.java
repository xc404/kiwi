package com.kiwi.project.ai.bpm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BpmDesignerXmlValidatorTest {

    private final BpmDesignerXmlValidator validator = new BpmDesignerXmlValidator();

    @Test
    void acceptsMinimalDefinitionsDefaultNamespace() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_1">
                  <process id="Process_1" isExecutable="true"/>
                </definitions>
                """;
        assertDoesNotThrow(() -> validator.validate(xml));
    }

    @Test
    void rejectsWrongRoot() {
        String xml = "<foo/>";
        assertThrows(IllegalArgumentException.class, () -> validator.validate(xml));
    }

    @Test
    void rejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("   "));
    }
}
