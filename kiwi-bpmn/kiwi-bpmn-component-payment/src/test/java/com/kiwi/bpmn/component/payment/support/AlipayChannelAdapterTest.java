package com.kiwi.bpmn.component.payment.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlipayChannelAdapterTest {

    private final AlipayChannelAdapter adapter = new AlipayChannelAdapter();

    @Test
    void parseCreateResponse_success() {
        String body =
                """
                {"alipay_trade_precreate_response":{"code":"10000","qr_code":"https://qr.example"}}
                """;
        var result = adapter.parseCreateResponse(body);
        assertEquals("https://qr.example", result.payUrl());
    }

    @Test
    void parseQueryResponse_mapsStatus() {
        String body =
                """
                {"alipay_trade_query_response":{"code":"10000","trade_status":"TRADE_SUCCESS","trade_no":"2024"}}
                """;
        var result = adapter.parseQueryResponse(body);
        assertEquals("SUCCESS", result.payStatus());
        assertEquals("2024", result.channelTradeNo());
    }

    @Test
    void mapAlipayStatus_pending() {
        assertEquals("PENDING", adapter.mapAlipayStatus("WAIT_BUYER_PAY"));
    }
}
