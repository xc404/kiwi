package com.kiwi.framework.security;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PasswordService
{
    @Value("${app.password.secret}")
    private String passwordSecret;

    public String encodePassword(String password) {
        return DigestUtil.sha256Hex(password + this.passwordSecret);
    }

    public static void main(String[] args) {
        String secret = System.getenv("APP_PASSWORD_SECRET");
        if (secret == null || secret.isBlank()) {
            System.err.println("请设置环境变量 APP_PASSWORD_SECRET 后再运行此工具。");
            System.exit(1);
        }
        PasswordService passwordService = new PasswordService();
        passwordService.passwordSecret = secret;
        System.out.println(passwordService.encodePassword("123456"));
    }

    public String getPasswordSecret() {
        return passwordSecret;
    }

}
