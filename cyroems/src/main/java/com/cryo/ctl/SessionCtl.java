package com.cryo.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.cryo.common.utils.CacheUtils;
import com.cryo.dao.UserRepository;
import com.cryo.model.user.Role;
import com.cryo.model.user.User;
import com.cryo.oauth.ctl.OAuthResult;
import com.cryo.oauth.service.OAuthPlatform;
import com.cryo.service.session.SessionService;
import com.cryo.service.session.SessionUser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;

@Controller
@AllArgsConstructor
@Slf4j
public class SessionCtl
{
    private final SessionService sessionService;
    //    private final JwtService jwtService;
    private final UserRepository userRepository;

    @PostMapping("/api/login")
    @ResponseBody
    public SessionUserOutput login(@RequestBody LoginInput input) {
        String username = input.username();
        if( !username.contains("@") ) {
            username = username + "@shanghaitech.edu.cn";
        }
        SessionUser user = this.sessionService.login(username, input.password);
        if( user == null ) {
            throw new RuntimeException("Invalid username or password");
        }
        return new SessionUserOutput(user);
    }

    @PostMapping("/api/v2/login")
    @ResponseBody
    public LoginResult loginV2(@RequestBody LoginInput input) {
        String username = input.username();
        if( !username.contains("@") ) {
            username = username + "@shanghaitech.edu.cn";
        }
        User user = this.sessionService.loginV2(username, input.password);
        if( user == null ) {
            throw new RuntimeException("Invalid username or password");
        }
        if( StringUtils.isNotBlank(input.token) ) {
//            Map<String, Object> verify = .verify(JwtService.JwtType.oauth_login_token, input.token);
            OAuthResult oAuthResult = CacheUtils.get(input.token);
            log.info("bind oauth user " + oAuthResult.getAuthUser().getUuid() + " to user " + user.getId());
            user.addOAuthUser(oAuthResult.getPlatform(), oAuthResult.getAuthUser());
            this.userRepository.save(user);
        }
        if( !(user.getRole() == Role.ADMIN || user.getTags().contains("admin")) ) {
            if( user.getOAuthUser(OAuthPlatform.Itech) == null ) {
                String token = CacheUtils.put(user, Duration.ofMinutes(15));
                return new LoginResult(LoginAction.BindUser, token);
            }
        }
        StpUtil.login(user.getId());
        return new LoginResult(LoginAction.LoginSuccess, StpUtil.getTokenValue());
    }

    @GetMapping("/api/get_user_info")
    @ResponseBody
    @SaCheckLogin
    public SessionUserOutput getUserInfo() {
        SessionUser sessionUser = this.sessionService.getSessionUser();
        return new SessionUserOutput(sessionUser);
    }

    public record LoginInput(@NotNull String username, @NotNull String password, @Nullable String token)
    {
    }

    public static class SessionUserOutput
    {
        @JsonIgnore
        private final SessionUser sessionUser;
        @JsonUnwrapped
        private final User user;

        public SessionUserOutput(SessionUser sessionUser) {
            this.sessionUser = sessionUser;
            this.user = sessionUser.getUser();
        }


        public String getToken() {
            return sessionUser.getToken();
        }
    }

    @Data
    @RequiredArgsConstructor
    public static class LoginResult
    {
        private final LoginAction action;
        private final String token;
    }

    public enum LoginAction
    {
        LoginSuccess,
        BindUser
    }
}
