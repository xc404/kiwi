package com.cryo.oauth.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xkcoding.http.config.HttpConfig;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.config.AuthSource;
import me.zhyd.oauth.request.AuthDefaultRequest;

import java.util.Optional;

public class OAuth2Config extends AuthConfig implements AuthSource, Cloneable
{
    private String platform;
    private String authorizeUrl;
    private String accessTokenUrl;
    private String userInfoUrl;
    private String revokeUrl;
    private String refreshTokenUrl;

    private Class<AuthDefaultRequest> targetClass;

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public void setAuthorizeUrl(String authorizeUrl) {
        this.authorizeUrl = authorizeUrl;
    }

    public void setAccessTokenUrl(String accessTokenUrl) {
        this.accessTokenUrl = accessTokenUrl;
    }

    public void setUserInfoUrl(String userInfoUrl) {
        this.userInfoUrl = userInfoUrl;
    }

    public void setRevokeUrl(String revokeUrl) {
        this.revokeUrl = revokeUrl;
    }

    public void setRefreshTokenUrl(String refreshTokenUrl) {
        this.refreshTokenUrl = refreshTokenUrl;
    }

    public String authorize(){
        return this.authorizeUrl;
    }

    /**
     * 获取accessToken的api
     *
     * @return url
     */
   public  String accessToken(){
       return this.accessTokenUrl;
   }

    /**
     * 获取用户信息的api
     *
     * @return url
     */
    public String userInfo(){
        return this.userInfoUrl;
    }

    /**
     * 取消授权的api
     *
     * @return url
     */
    public String revoke() {
        return this.revokeUrl;
    }

    /**
     * 刷新授权的api
     *
     * @return url
     */
    public String refresh() {
        return this.refreshTokenUrl;
    }

    /**
     * 获取Source的字符串名字
     *
     * @return name
     */
    public String getName() {
        return this.platform;
    }

    /**
     * 平台对应的 AuthRequest 实现类，必须继承自 {@link AuthDefaultRequest}
     *
     * @return class
     */
    public Class<? extends AuthDefaultRequest> getTargetClass(){

        return Optional.ofNullable(this.targetClass).orElse((Class)OAuth2Request.class);
    }

    public void setTargetClass(Class<AuthDefaultRequest> targetClass) {
        this.targetClass = targetClass;
    }

    @Override
    public OAuth2Config clone() {
        try {
            OAuth2Config clone = (OAuth2Config) super.clone();
            // TODO: copy mutable state here, so the clone can't change the internals of the original
            return clone;
        } catch( CloneNotSupportedException e ) {
            throw new AssertionError();
        }
    }

    @JsonCreator
    public OAuth2Config() {
    }

    @JsonIgnore
    @Override
    public void setHttpConfig(HttpConfig httpConfig) {
        super.setHttpConfig(httpConfig);
    }
}
