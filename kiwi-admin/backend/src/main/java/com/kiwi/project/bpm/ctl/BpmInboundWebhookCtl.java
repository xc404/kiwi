package com.kiwi.project.bpm.ctl;

import com.kiwi.project.bpm.service.BpmInboundCorrelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 入站 Webhook 触发端点（无需登录；可选 X-Kiwi-Inbound-Token）。
 */
@RestController
@RequestMapping("bpm/inbound")
@RequiredArgsConstructor
@Tag(name = "BPM 入站 Webhook", description = "外部系统 POST 触发 Message 关联")
public class BpmInboundWebhookCtl {

    private final BpmInboundCorrelationService inboundCorrelationService;

    @Operation(operationId = "bpmInbound_trigger", summary = "触发入站 Message 关联")
    @PostMapping("{componentKey}")
    @ResponseBody
    public InboundCorrelateResponse trigger(
            @PathVariable String componentKey,
            @RequestHeader(value = "X-Kiwi-Inbound-Token", required = false) String inboundToken,
            @RequestBody(required = false) Map<String, Object> variables) {
        int count = inboundCorrelationService.correlate(componentKey, inboundToken, variables);
        InboundCorrelateResponse res = new InboundCorrelateResponse();
        res.setCorrelatedCount(count);
        res.setComponentKey(componentKey);
        return res;
    }

    @Data
    public static class InboundCorrelateResponse {
        private String componentKey;
        private int correlatedCount;
    }
}
