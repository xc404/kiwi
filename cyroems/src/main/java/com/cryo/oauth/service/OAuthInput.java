package com.cryo.oauth.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OAuthInput
{
    private String returnUrl;
    private OAuthPlatform platform;
    private OAuthAction action;
    private String token;
}
