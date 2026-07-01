package com.kiwi.bpmn.component.payment.model;

public record PaymentCreateResult(String prepayPayload, String payUrl, String rawResponse) {}
