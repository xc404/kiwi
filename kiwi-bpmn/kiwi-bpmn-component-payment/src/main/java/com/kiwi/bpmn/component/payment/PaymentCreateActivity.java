package com.kiwi.bpmn.component.payment;

import com.kiwi.bpmn.component.payment.model.PaymentCreateRequest;
import com.kiwi.bpmn.component.payment.model.PaymentCreateResult;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.bpmn.core.utils.ExecutionUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@ComponentDescription(
        name = "支付下单",
        group = "支付",
        version = "1.0",
        description = "按 channel（alipay|wechat）创建支付单；凭证建议用项目环境变量注入",
        inputs = {
                @ComponentParameter(key = "channel", description = "支付渠道：alipay 或 wechat", required = true),
                @ComponentParameter(key = "outTradeNo", description = "商户订单号", required = true),
                @ComponentParameter(key = "amount", description = "金额（分）", required = true),
                @ComponentParameter(key = "subject", description = "商品标题", required = true),
                @ComponentParameter(key = "alipay_app_id", description = "支付宝 AppId"),
                @ComponentParameter(key = "alipay_private_key", description = "支付宝应用私钥 PEM"),
                @ComponentParameter(
                        key = "alipay_gateway_url",
                        description = "支付宝网关，默认沙箱"),
                @ComponentParameter(key = "wechat_app_id", description = "微信 AppId"),
                @ComponentParameter(key = "wechat_mch_id", description = "微信商户号"),
                @ComponentParameter(key = "wechat_api_v3_key", description = "微信 APIv3 密钥"),
                @ComponentParameter(key = "wechat_cert_serial", description = "微信商户证书序列号"),
                @ComponentParameter(key = "wechat_private_key", description = "微信商户私钥 PEM"),
                @ComponentParameter(key = "notify_url", description = "支付结果回调 URL")
        },
        outputs = {
                @ComponentParameter(key = "prepayPayload", description = "渠道原始响应 JSON"),
                @ComponentParameter(key = "payUrl", description = "支付二维码链接或 code_url"),
                @ComponentParameter(key = "rawResponse", schema = @Schema(defaultValue = "rawResponse"))
        })
@Component("paymentCreate")
@RequiredArgsConstructor
public class PaymentCreateActivity implements JavaDelegate {

    private final PaymentChannelRouter paymentChannelRouter;

    @Override
    public void execute(DelegateExecution execution) {
        PaymentCreateRequest request = new PaymentCreateRequest(
                ExecutionUtils.requireStringInputVariable(execution, "channel"),
                ExecutionUtils.requireStringInputVariable(execution, "outTradeNo"),
                ExecutionUtils.getIntInputVariable(execution, "amount")
                        .map(Integer::longValue)
                        .orElseThrow(() -> new IllegalArgumentException("amount 不能为空")),
                ExecutionUtils.requireStringInputVariable(execution, "subject"),
                ExecutionUtils.getStringInputVariable(execution, "alipay_app_id").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "alipay_private_key").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "alipay_gateway_url").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "wechat_app_id").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "wechat_mch_id").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "wechat_api_v3_key").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "wechat_cert_serial").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "wechat_private_key").orElse(null),
                ExecutionUtils.getStringInputVariable(execution, "notify_url").orElse(null));
        PaymentCreateResult result = paymentChannelRouter.create(request);
        setOutput(execution, "prepayPayload", result.prepayPayload());
        setOutput(execution, "payUrl", result.payUrl());
        setOutput(execution, "rawResponse", result.rawResponse());
    }

    private void setOutput(DelegateExecution execution, String key, String value) {
        String varName = ExecutionUtils.getOutputVariableName(execution, key);
        if (varName != null && !varName.isBlank()) {
            execution.setVariable(varName, value);
        }
    }
}
