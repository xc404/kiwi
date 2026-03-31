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
        PasswordService passwordService = new PasswordService();
        passwordService.passwordSecret = "rrcpvkorgwxrxzxdsympopqancftjjux";
        System.out.println(passwordService.encodePassword("123456"));
    }

    public String getPasswordSecret() {
        return passwordSecret;
    }

}
