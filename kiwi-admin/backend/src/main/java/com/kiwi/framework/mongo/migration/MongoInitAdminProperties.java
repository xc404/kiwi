package com.kiwi.framework.mongo.migration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 首次管理员用户 Mongock 迁移（{@code InitAdminUserChangeUnit}）配置。
 * 对应 {@code kiwi.mongodb.init.*}，可通过环境变量 {@code KIWI_MONGODB_INIT_ADMIN_USERNAME} 等覆盖。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kiwi.mongodb.init")
public class MongoInitAdminProperties {

    private String adminUsername = "admin";

    private String adminPassword = "";

    private String adminNickName = "Administrator";
}
