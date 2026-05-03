package com.cryo.oauth.ctl;

import com.cryo.model.user.User;
import com.cryo.oauth.service.OAuthAction;
import com.cryo.oauth.service.OAuthPlatform;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.zhyd.oauth.model.AuthUser;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OAuthResult
{
    private User user;
    private OAuthPlatform platform;
    private AuthUser authUser;
    private OAuthAction.ResponseAction action;
}
