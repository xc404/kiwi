package com.kiwi.bpmn.component.json;

import com.fasterxml.jackson.databind.JsonNode;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonMapExecutorTest {

    private JsonMapExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JsonMapExecutor();
    }

    @Test
    void applyMappings_fromJsonString() {
        JsonNode root = executor.toJsonNode("{\"data\":{\"id\":\"abc\"}}");
        List<JsonMapExecutor.MappingEntry> mappings = executor.parseMappings(
                "[{\"key\":\"task_id\",\"value\":\"/data/id\"}]");
        Map<String, Object> out = executor.applyMappings(root, mappings);
        assertEquals("abc", out.get("task_id"));
    }

    @Test
    void applyMappings_fromMap() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("items", List.of(Map.of("name", "n1")));
        JsonNode root = executor.toJsonNode(source);
        List<JsonMapExecutor.MappingEntry> mappings = executor.parseMappings(
                "[{\"key\":\"first_name\",\"value\":\"/items/0/name\"}]");
        Map<String, Object> out = executor.applyMappings(root, mappings);
        assertEquals("n1", out.get("first_name"));
    }

    @Test
    void applyMappings_fromDocument() {
        Document doc = new Document("k", "v");
        JsonNode root = executor.toJsonNode(doc);
        List<JsonMapExecutor.MappingEntry> mappings = executor.parseMappings("{\"out\":\"/k\"}");
        Map<String, Object> out = executor.applyMappings(root, mappings);
        assertEquals("v", out.get("out"));
    }

    @Test
    void applyMappings_missingPointerFails() {
        JsonNode root = executor.toJsonNode("{\"a\":1}");
        List<JsonMapExecutor.MappingEntry> mappings = executor.parseMappings("[{\"key\":\"x\",\"value\":\"/missing\"}]");
        assertThrows(IllegalArgumentException.class, () -> executor.applyMappings(root, mappings));
    }

    @Test
    void parseMappings_emptyFails() {
        assertThrows(IllegalArgumentException.class, () -> executor.parseMappings("[]"));
    }

    @Test
    void toJsonNode_invalidStringFails() {
        assertThrows(IllegalArgumentException.class, () -> executor.toJsonNode("{bad"));
    }
}
