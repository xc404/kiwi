package com.cryo.oauth.service;

import me.zhyd.oauth.model.AuthUser;

public interface OAuthHandler
{
    public void onAuthComplete(OAuthInput oAuthInput, AuthUser authUser);
    public boolean support(OAuthAction authMode);
}
