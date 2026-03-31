package com.kiwi.project.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 与前端 {@code NotificationItem} 对齐。
 */
@Data
@Schema(description = "站内消息")
public class NotificationDto {

    private String id;

    /** notice | message | todo */
    private String channel;

    private String title;

    private String summary;

    private String createdAt;

    private boolean read;

    private TagDto tag;

    private String extra;

    @Data
    @Schema(description = "标签")
    public static class TagDto {
        private String text;
        private String color;
    }
}
