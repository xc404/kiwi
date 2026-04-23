package com.cryo.oauth.service;

public enum OAuthAction
{
    Login,
    BindUser;

    public enum ResponseAction
    {

        LoginSuccess,
        NoUserMatch;
    }
}
