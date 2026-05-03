package com.cryo.oauth.service;

import com.alibaba.fastjson.JSONObject;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthDefaultRequest;
import net.dreamlu.mica.core.utils.JsonUtil;

public class OAuth2Request extends AuthDefaultRequest
{
    public OAuth2Request(AuthConfig oAuth2Config) {
        super(oAuth2Config, (OAuth2Config)oAuth2Config);
    }

    @Override
    public AuthToken getAccessToken(AuthCallback authCallback) {
        String s = doPostAuthorizationCode(authCallback.getCode());
        JSONObject accessTokenObject = JSONObject.parseObject(s);
        return AuthToken.builder()
                .accessToken(accessTokenObject.getString("access_token"))
                .expireIn(accessTokenObject.getIntValue("expires_in"))
                .tokenType(accessTokenObject.getString("token_type"))
                .idToken(accessTokenObject.getString("id_token"))
                .refreshToken(accessTokenObject.getString("refresh_token"))
                .build();
    }


    @Override
    public AuthUser getUserInfo(AuthToken authToken) {
        String s = super.doGetUserInfo(authToken);
        return JsonUtil.readValue(s, AuthUser.class);
    }

    @Override
    public AuthResponse revoke(AuthToken authToken) {
        return super.revoke(authToken);
    }

    @Override
    public AuthResponse refresh(AuthToken authToken) {
        return super.refresh(authToken);
    }
}
