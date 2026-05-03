package com.cryo.common.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.lang.generator.UUIDGenerator;
import cn.hutool.core.util.IdUtil;
import me.zhyd.oauth.utils.UuidUtils;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.text.RandomStringGenerator;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class CacheUtils
{
    private final static ExpiringMap<String, Object> cache = ExpiringMap.builder()
            .expiration(1, TimeUnit.HOURS)
            .variableExpiration().build();


    public static String put(Object value) {
        String key = UUID.fastUUID().toString(true);
        cache.put(key, value);
        return key;
    }


    public static String put(Object value, Duration ttl) {

        String key = UUID.fastUUID().toString(true);
        cache.put(key, value, ttl.toSeconds(), TimeUnit.SECONDS);
        return key;
    }

    public static <T> T get(String key) {
        return (T) cache.get(key);
    }

    public static <T> T redeem(String key) {
        return (T) cache.remove(key);
    }
}
