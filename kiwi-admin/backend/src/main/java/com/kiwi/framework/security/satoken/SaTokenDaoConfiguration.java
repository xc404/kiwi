package com.kiwi.framework.security.satoken;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.util.Assert;

/**
 * Sa-Token 持久化：由 {@code kiwi.sa-token.storage} 指定 {@code redis} 或 {@code mongodb}（默认）。
 */
@Configuration
public class SaTokenDaoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "kiwi.sa-token", name = "storage", havingValue = "redis")
    @ConditionalOnBean(RedisConnectionFactory.class)
    public SaTokenDao saTokenDaoRedis(RedisConnectionFactory connectionFactory) {
        Assert.notNull(connectionFactory, "kiwi.sa-token.storage=redis 时需配置 spring.data.redis");
        SaTokenDaoForRedisTemplate dao = new SaTokenDaoForRedisTemplate();
        dao.init(connectionFactory);
        return dao;
    }

    @Bean
    @ConditionalOnProperty(prefix = "kiwi.sa-token", name = "storage", havingValue = "mongodb", matchIfMissing = true)
    public SaTokenDao saTokenDaoMongo(@Qualifier("mongoTemplate") MongoTemplate mongoTemplate) {
        return new SaTokenMongoDao(mongoTemplate);
    }
}
