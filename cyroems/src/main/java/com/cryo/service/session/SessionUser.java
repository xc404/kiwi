package com.cryo.service.session;

import com.cryo.model.user.User;
import lombok.Getter;

@Getter
public class SessionUser {
    private final User user;
    private final String token;
    public SessionUser(User user,  String token) {
        this.user = user;
        this.token = token;
    }

}
