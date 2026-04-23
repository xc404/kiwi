package com.cryo.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    @Value("${app.password.salt}")
    private String passwordSalt;

    public String encodePassword(String password) {
        return DigestUtil.sha256Hex(password + this.passwordSalt);
    }


    public static void main(String[] args) {
        PasswordService passwordService = new PasswordService();
        passwordService.passwordSalt = "66a27b7c363fa5fd54a7bf47dd785da4d39a6a645fdc0c5fa31191174ed5c507";
        System.out.println(passwordService.encodePassword("123456"));
    }

}
