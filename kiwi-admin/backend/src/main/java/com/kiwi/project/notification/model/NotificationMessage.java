package com.kiwi.project.notification.model;

import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 站内消息（按用户维度存储）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Document("notification_message")
public class NotificationMessage extends BaseEntity<String> {

    /** 接收用户 ID */
    private String userId;

    /** notice | message | todo */
    private String channel;

    private String title;

    private String summary;

    private Boolean read;

    private String tagText;

    private String tagColor;

    private String extra;
}
