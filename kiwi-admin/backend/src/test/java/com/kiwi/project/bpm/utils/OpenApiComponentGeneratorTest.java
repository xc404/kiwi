package com.kiwi.project.bpm.utils;

import com.kiwi.project.bpm.model.BpmComponent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiComponentGeneratorTest {

    private static final String MINIMAL_OPENAPI =
            """
            openapi: 3.0.0
            info:
              title: T
              version: "1"
            servers:
              - url: https://api.example.com/v1
            paths:
              /pets/{id}:
                get:
                  summary: 获取宠物
                  operationId: getPet
                post:
                  summary: 更新宠物
                  operationId: updatePet
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
            """;

    @Test
    void buildComponents_onePerOperation() {
        List<BpmComponent> list =
                OpenApiComponentGenerator.buildComponents(MINIMAL_OPENAPI, null, "classpath_httpRequest");
        assertEquals(2, list.size());
        List<String> keys =
                list.stream().map(BpmComponent::getKey).sorted().collect(Collectors.toList());
        assertEquals(List.of("openapi_getpet", "openapi_updatepet"), keys);
        BpmComponent get = list.stream().filter(c -> "openapi_getpet".equals(c.getKey())).findFirst().orElseThrow();
        assertEquals("classpath_httpRequest", get.getParentId());
        assertTrue(
                get.getInputParameters().stream()
                        .anyMatch(
                                p ->
                                        "path_id".equals(p.getKey())
                                                && "Path".equals(p.getGroup())));
        assertTrue(
                Objects.requireNonNull(get.getInputParameters()).stream()
                        .anyMatch(
                                p ->
                                        "url".equals(p.getKey())
                                                && Boolean.TRUE.equals(p.getHidden())
                                                && "https://api.example.com/v1/pets/${path_id}"
                                                        .equals(p.getDefaultValue())));
        assertTrue(
                get.getInputParameters().stream()
                        .anyMatch(p -> "method".equals(p.getKey()) && "GET".equals(p.getDefaultValue())));
    }

    @Test
    void joinBaseAndPath() {
        assertEquals("/a", OpenApiComponentGenerator.joinBaseAndPath("", "/a"));
        assertEquals("https://x.com/a", OpenApiComponentGenerator.joinBaseAndPath("https://x.com", "/a"));
    }

    private static final String OPENAPI_BODY_PROPS =
            """
            openapi: 3.0.0
            info:
              title: T
              version: "1"
            servers:
              - url: https://api.example.com
            paths:
              /items:
                post:
                  summary: 创建
                  operationId: createItem
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required: [name]
                          properties:
                            name:
                              type: string
                            count:
                              type: integer
            """;

    @Test
    void buildComponents_jsonBodyProperties_composedIntoHiddenBody() {
        BpmComponent post =
                OpenApiComponentGenerator.buildComponents(
                                OPENAPI_BODY_PROPS, null, "classpath_httpRequest")
                        .stream()
                        .filter(c -> "openapi_createitem".equals(c.getKey()))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                post.getInputParameters().stream()
                        .anyMatch(p -> "body_name".equals(p.getKey()) && "Body".equals(p.getGroup())));
        assertTrue(
                post.getInputParameters().stream()
                        .anyMatch(
                                p ->
                                        "body".equals(p.getKey())
                                                && Boolean.TRUE.equals(p.getHidden())
                                                && p.getDefaultValue() != null
                                                && p.getDefaultValue().contains("${body_name}")
                                                && p.getDefaultValue().contains("${body_count}")));
    }
}
