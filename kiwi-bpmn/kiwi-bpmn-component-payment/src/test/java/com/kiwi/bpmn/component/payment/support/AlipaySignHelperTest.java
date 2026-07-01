package com.kiwi.bpmn.component.payment.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AlipaySignHelperTest {

    @Test
    void canonicalize_sortsAndOmitsSign() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("b", "2");
        params.put("a", "1");
        params.put("sign", "ignored");
        assertEquals("a=1&b=2", AlipaySignHelper.canonicalize(params));
    }

    @Test
    void buildFormBody_urlEncodes() {
        String body = AlipaySignHelper.buildFormBody(Map.of("a", "x y", "b", "1"));
        assertFalse(body.isBlank());
    }
}
