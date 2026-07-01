package com.kiwi.bpmn.component.payment.model;

public record PaymentCreateRequest(
        String channel,
        String outTradeNo,
        long amountFen,
        String subject,
        String alipayAppId,
        String alipayPrivateKey,
        String alipayGatewayUrl,
        String wechatAppId,
        String wechatMchId,
        String wechatApiV3Key,
        String wechatCertSerial,
        String wechatPrivateKey,
        String notifyUrl) {}
