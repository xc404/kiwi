package com.cryo.oauth.service;

import com.cryo.model.user.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.zhyd.oauth.model.AuthUser;

@Data
@AllArgsConstructor
public class OAuthCompleteResult
{
    private OAuthInput authInput;
    private AuthUser authUser;
    private User user;

}
