package com.cryo.service;

import cn.hutool.jwt.JWT;
import lombok.Data;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService implements InitializingBean {
    private Map<JwtType, JwtConfig> configs = new HashMap<>();
    private static final  String issue = "shanghaitech.edu.cn";
    public enum JwtType {
        register,
        password_reset,
        oauth_bind_user,
        oauth_login_token;
    }

    @Data
    public static class JwtConfig {
        private long ttl;
        private String key;
    }

    public String sign(JwtType type, Map<String, Object> p) {
        JwtConfig jwtConfig = configs.get(type);
        Instant instant = Instant.now().plus(Duration.ofSeconds(jwtConfig.ttl));
        String sign = JWT.create().addPayloads(p).setIssuedAt(new Date()).setExpiresAt(Date.from(instant))
                .setKey(jwtConfig.getKey().getBytes(StandardCharsets.UTF_8))
                .setIssuer(issue)
                .sign();
        return sign;
    }

    public Map<String,Object> verify(JwtType type, String token) {
        JwtConfig jwtConfig = configs.get(type);
        JWT parse = JWT.create().setKey(jwtConfig.getKey().getBytes(StandardCharsets.UTF_8)).parse(token);
        boolean validate = parse.validate(100);
        if(!validate){
            throw new RuntimeException("invalid token");
        }
        return parse.getPayloads();
    }
    @Override
    public void afterPropertiesSet() throws Exception {
        File file = ResourceUtils.getFile("classpath:jwt.json");
        this.configs = JsonUtil.readMap(new FileInputStream(file), JwtType.class, JwtConfig.class);
    }
}
