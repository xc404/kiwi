package com.kiwi.framework.security.satoken;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * Sa-Token 会话与 Token 的 MongoDB 持久化文档；{@code timeout} 使用 TTL 索引自动清理过期数据。
 */
@Data
@Document(collection = "sa_token_cache")
public class SaTokenCacheEntry {

    @Id
    private String id;

    private String value;

    private Object object;

    /** 为 {@code null} 表示永不过期；否则由 MongoDB TTL 在到期后删除。 */
    @Indexed(expireAfter = "1s")
    private Date timeout;

    boolean isLive() {
        return timeout == null || timeout.after(new Date());
    }
}
