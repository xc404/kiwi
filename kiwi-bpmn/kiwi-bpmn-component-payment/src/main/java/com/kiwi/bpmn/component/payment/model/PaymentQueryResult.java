package com.kiwi.bpmn.component.payment.model;

public record PaymentQueryResult(String payStatus, String channelTradeNo, String rawResponse) {}
