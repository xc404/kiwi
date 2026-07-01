package com.kiwi.bpmn.component.payment;

import com.kiwi.bpmn.component.payment.model.PaymentCreateRequest;
import com.kiwi.bpmn.component.payment.model.PaymentCreateResult;
import com.kiwi.bpmn.component.payment.model.PaymentQueryRequest;
import com.kiwi.bpmn.component.payment.model.PaymentQueryResult;
import com.kiwi.bpmn.component.payment.support.AlipayChannelAdapter;
import com.kiwi.bpmn.component.payment.support.WechatChannelAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentChannelRouter {

    private final AlipayChannelAdapter alipayChannelAdapter;
    private final WechatChannelAdapter wechatChannelAdapter;

    public PaymentCreateResult create(PaymentCreateRequest request) {
        return switch (normalizeChannel(request.channel())) {
            case "alipay" -> alipayChannelAdapter.create(request);
            case "wechat" -> wechatChannelAdapter.create(request);
            default -> throw new IllegalArgumentException("不支持的支付渠道: " + request.channel());
        };
    }

    public PaymentQueryResult query(PaymentQueryRequest request) {
        return switch (normalizeChannel(request.channel())) {
            case "alipay" -> alipayChannelAdapter.query(request);
            case "wechat" -> wechatChannelAdapter.query(request);
            default -> throw new IllegalArgumentException("不支持的支付渠道: " + request.channel());
        };
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel 不能为空");
        }
        return channel.trim().toLowerCase();
    }
}
