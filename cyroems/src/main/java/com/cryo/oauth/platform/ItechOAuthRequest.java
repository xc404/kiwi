package com.cryo.oauth.platform;

import com.alibaba.fastjson.JSONObject;
import com.cryo.oauth.service.OAuth2Request;
import com.cryo.oauth.service.OAuthPlatform;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;


@Slf4j
public class ItechOAuthRequest extends OAuth2Request
{
    public ItechOAuthRequest(AuthConfig oAuth2Config) {
        super(oAuth2Config);
    }

    @Override
    public AuthUser getUserInfo(AuthToken authToken) {
        String s = super.doGetUserInfo(authToken);
//        return JsonUtil.readValue(s, AuthUser.class);
        log.info("get user response:{}", s);
        JSONObject jsonObject = JSONObject.parseObject(s);
        return AuthUser.builder()
                .rawUserInfo(jsonObject)
                .uuid(jsonObject.getString("id"))
                .email(jsonObject.getJSONObject("attributes").getString("securityEmail"))
                .username(jsonObject.getJSONObject("attributes").getString("cn"))
                .token(authToken)
                .source(OAuthPlatform.Itech.name())
                .build();
    }
}
