package com.cryo.model.user;

import com.cryo.oauth.service.OAuthPlatform;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import me.zhyd.oauth.model.AuthUser;

public class UserDetail
{
    @JsonUnwrapped
    private final User user;

    @JsonUnwrapped
    private final Role role;

    @JsonUnwrapped(prefix = "group_")
    private final Group group;

    private final String oauthUsername;

    public UserDetail(User user, Role role, Group group) {
        this.user = user;
        this.role = role;
        this.group = group;
        AuthUser itechUser = user.getOAuthUser(OAuthPlatform.Itech);
        this.oauthUsername = itechUser != null ? itechUser.getUsername() : user.getSys_username();
    }

    public String getOauthUsername() {
        return oauthUsername;
    }
}
