package com.kiwi.bpmn.component.payment;

import com.kiwi.bpmn.component.payment.support.AlipayChannelAdapter;
import com.kiwi.bpmn.component.payment.support.WechatChannelAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentChannelRouterTest {

    private final PaymentChannelRouter router =
            new PaymentChannelRouter(new AlipayChannelAdapter(), new WechatChannelAdapter());

    @Test
    void normalizeChannel_rejectsUnknown() {
        assertThrows(
                IllegalArgumentException.class,
                () -> router.create(new com.kiwi.bpmn.component.payment.model.PaymentCreateRequest(
                        "stripe",
                        "o1",
                        100L,
                        "s",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));
    }
}
