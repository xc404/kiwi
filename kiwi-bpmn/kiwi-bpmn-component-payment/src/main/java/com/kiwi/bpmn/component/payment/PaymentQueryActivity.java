package com.kiwi.bpmn.component.payment;

import com.kiwi.bpmn.component.payment.model.PaymentQueryRequest;
import com.kiwi.bpmn.component.payment.model.PaymentQueryResult;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.bpmn.core.utils.ExecutionUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@ComponentDescription(
        name = "支付查单",
        group = "支付",
        version = "1.0",
        description = "按 channel 与 outTradeNo 查询支付状态",
        inputs = {
                @ComponentParameter(key = "channel", description = "支付渠道：alipay 或 wechat", required = true),
                @ComponentParameter(key = "outTradeNo", description = "商户订单号", required = true),
                @ComponentParameter(key = "alipay_app_id", description = "支付宝 AppId"),
                @ComponentParameter(key = "alipay_private_key", description = "支付宝应用私钥 PEM"),
                @ComponentParameter(key = "alipay_gateway_url", description = "支付宝网关"),
                @ComponentParameter(key = "wechat_mch_id", description = "微信商户号"),
                @ComponentParameter(key = "wechat_api_v3_key", description = "微信 APIv3 密钥"),
                @ComponentParameter(key = "wechat_cert_serial", description = "微信商户证书序列号"),
                @ComponentParameter(key = "wechat_private_key", description = "微信商户私钥 PEM")
        },
        outputs = {
                @ComponentParameter(key = "payStatus", description = "SUCCESS / PENDING / CLOSED 等"),
                @ComponentParameter(key = "channelTradeNo", description = "渠道交易号"),
                @ComponentParameter(key = "rawResponse", schema = @Schema(defaultValue = "rawResponse"))
        })
@Component("paymentQuery")
@RequiredArgsConstructor
public class PaymentQueryActivity implements JavaDelegate {

    private final PaymentChannelRouter paymentChannelRouter;

    @Override
    public void execute(DelegateExecution execution) {
        PaymentQueryRequest request = new PaymentQueryRequest(
                ExecutionUtils.requireStringInputVariable(execution, "channel"),
                ExecutionUtils.requireStringInputVariable(execution, "outTradeNo"),
                ExecutionUtils.getStringInputVariable(execution, "alipay_app_id").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "alipay_private_key").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "alipay_gateway_url").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "wechat_mch_id").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "wechat_api_v3_key").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "wechat_cert_serial").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "wechat_private_key").orElse(null));
        PaymentQueryResult result = paymentChannelRouter.query(request);
        setOutput(execution, "payStatus", result.payStatus());
        setOutput(execution, "channelTradeNo", result.channelTradeNo());
        setOutput(execution, "rawResponse", result.rawResponse());
    }

    private void setOutput(DelegateExecution execution, String key, String value) {
        String varName = ExecutionUtils.getOutputVariableName(execution, key);
        if (varName != null && !varName.isBlank()) {
            execution.setVariable(varName, value);
        }
    }
}
