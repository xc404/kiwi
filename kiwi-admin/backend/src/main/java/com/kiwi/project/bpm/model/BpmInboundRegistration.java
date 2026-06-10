package com.kiwi.project.bpm.model;

import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 入站 Webhook 与 BPMN Message 的注册映射。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Document("bpmInboundRegistration")
public class BpmInboundRegistration extends BaseEntity<String> {

    /** URL 路径段，全局唯一，如 {@code github-push} */
    @Indexed(unique = true)
    private String componentKey;

    /** BPMN 中间捕获事件的 message 名称 */
    private String messageName;

    /** 可选：仅关联指定项目的流程实例 */
    private String projectId;

    /** 可选：调用方须在请求头 X-Kiwi-Inbound-Token 携带 */
    private String secretToken;

    private String description;

    private Boolean enabled;
}
