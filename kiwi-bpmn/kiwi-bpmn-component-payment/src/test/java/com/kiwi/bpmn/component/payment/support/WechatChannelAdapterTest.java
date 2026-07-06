package com.kiwi.bpmn.component.payment.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WechatChannelAdapterTest {

    private final WechatChannelAdapter adapter = new WechatChannelAdapter();

    @Test
    void parseCreateResponse_success() {
        String body =
                """
                {"code_url":"weixin://wxpay/bizpayurl?pr=abc"}
                """;
        var result = adapter.parseCreateResponse(body);
        assertEquals("weixin://wxpay/bizpayurl?pr=abc", result.payUrl());
    }

    @Test
    void parseQueryResponse_mapsStatus() {
        String body =
                """
                {"trade_state":"SUCCESS","transaction_id":"4200001234"}
                """;
        var result = adapter.parseQueryResponse(body);
        assertEquals("SUCCESS", result.payStatus());
        assertEquals("4200001234", result.channelTradeNo());
    }

    @Test
    void mapWechatStatus_pending() {
        assertEquals("PENDING", adapter.mapWechatStatus("NOTPAY"));
        assertEquals("PENDING", adapter.mapWechatStatus("USERPAYING"));
    }

    @Test
    void mapWechatStatus_closed() {
        assertEquals("CLOSED", adapter.mapWechatStatus("CLOSED"));
    }
}
