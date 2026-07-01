package com.kiwi.bpmn.component.payment.model;

public record PaymentQueryRequest(
        String channel,
        String outTradeNo,
        String alipayAppId,
        String alipayPrivateKey,
        String alipayGatewayUrl,
        String wechatMchId,
        String wechatApiV3Key,
        String wechatCertSerial,
        String wechatPrivateKey) {}
