package com.cryo.oauth.ctl;

import cn.dev33.satoken.stp.StpUtil;
import com.cryo.common.model.IdEntity;
import com.cryo.common.utils.CacheUtils;
import com.cryo.dao.UserRepository;
import com.cryo.model.user.User;
import com.cryo.oauth.service.OAuthAction;
import com.cryo.oauth.service.OAuthCompleteResult;
import com.cryo.oauth.service.OAuthInput;
import com.cryo.oauth.service.OAuthPlatform;
import com.cryo.oauth.service.OAuthService;
import com.cryo.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.enums.AuthUserGender;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

import java.time.Duration;

@Controller
@RequiredArgsConstructor
@Slf4j
public class OAuthCtl
{
    private final OAuthService oAuthService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final boolean autoBind = true;

    @Operation(ignoreJsonView = true, summary = "OAuth登录")
    @GetMapping("/api/oauth/login")
    public View oauth(String returnUrl, OAuthPlatform platform, OAuthAction action, String token) {
        if( platform == null ) {
            platform = OAuthPlatform.Itech;
        }
        if( action == null ) {
            action = OAuthAction.Login;
        }
        OAuthInput oAuthInput = new OAuthInput(returnUrl, platform, action, token);
        return new RedirectView(this.oAuthService.oauth(oAuthInput));
    }

    @Operation(ignoreJsonView = true, summary = "OAuth登录完成")

    @GetMapping("/api/oauth/complete")
    public View oauth_complete(AuthCallback authCallback) {

        OAuthCompleteResult complete = this.oAuthService.complete(authCallback);

        try {
            OAuthAction.ResponseAction action = null;
            switch( complete.getAuthInput().getAction() ) {
                case Login:
                    if( complete.getUser() != null ) {
                        User user = complete.getUser();
                        AuthUser oAuthUser = user.getOAuthUser(complete.getAuthInput().getPlatform());
                        if( oAuthUser == null ) {
                            if( autoBind ) {
                                log.info("bind oauth user " + complete.getAuthUser().getUuid() + " to user " + user.getId());
                                user.addOAuthUser(complete.getAuthInput().getPlatform(), complete.getAuthUser());
                                this.userRepository.save(user);
                                action = OAuthAction.ResponseAction.LoginSuccess;
                            } else {
                                action = OAuthAction.ResponseAction.NoUserMatch;
                            }
                        } else {
                            if( oAuthUser.getUuid().equals(complete.getAuthUser().getUuid()) ) {
                                action = OAuthAction.ResponseAction.LoginSuccess;
                            } else {
                                action = OAuthAction.ResponseAction.NoUserMatch;
                            }
                        }
                    } else {
                        action = OAuthAction.ResponseAction.NoUserMatch;
                    }
                    break;
                case BindUser:

                    IdEntity idEntity = CacheUtils.redeem(complete.getAuthInput().getToken());
                    String id = idEntity.getId();
                    User user = this.userRepository.findById(id).orElse(null);
                    if( user == null ) {
                        throw new RuntimeException("user not exist");
                    }
                    if( complete.getUser() != null && !complete.getUser().getId().equals(user.getId()) ) {
                        throw new RuntimeException("this account has been binded to another user (" + complete.getUser().getEmail() + ")");
                    }
                    log.info("bind oauth user " + complete.getAuthUser().getUuid() + " to user " + id);
                    user.addOAuthUser(complete.getAuthInput().getPlatform(), complete.getAuthUser());
                    this.userRepository.save(user);
                    action = OAuthAction.ResponseAction.LoginSuccess;
                    complete.setUser(user);
                    break;
            }
            String token = createToken(complete, action);
            return new RedirectView(this.oAuthService.backToClient(complete.getAuthInput(), token));
        } catch( Exception e ) {

            return new RedirectView(this.oAuthService.backToClient(complete.getAuthInput(), e));
        }
    }

    private String createToken(OAuthCompleteResult completeResult, OAuthAction.ResponseAction action) {
        OAuthResult oAuthResult = new OAuthResult(completeResult.getUser(), completeResult.getAuthInput().getPlatform(), completeResult.getAuthUser(), action);
//        Map map = JsonUtil.convertValue(oAuthResult, Map.class);
        String token = CacheUtils.put(oAuthResult, Duration.ofMinutes(15));
        return token;

    }

    @PostMapping("/api/oauth/token/login")
    @ResponseBody
    public LoginResult tokenLogin(@RequestBody TokenInput tokenInput) {
        OAuthResult oAuthResult = CacheUtils.get(tokenInput.token);
        if( oAuthResult.getAction() == OAuthAction.ResponseAction.LoginSuccess ) {
            StpUtil.login(oAuthResult.getUser().getId());
            String tokenValue = StpUtil.getTokenValue();
            return new LoginResult(oAuthResult.getAction(), tokenValue);
        }
        if( oAuthResult.getAction() == OAuthAction.ResponseAction.NoUserMatch ) {
            return new LoginResult(oAuthResult.getAction(), tokenInput.getToken());
        }
        throw new RuntimeException("invalid token");
    }

    @GetMapping("/api/oauth/user/info")
    @ResponseBody
    public UserInfoOutput userInfo(String token) {
        OAuthResult oAuthResult = CacheUtils.get(token);
        return new UserInfoOutput(oAuthResult.getAuthUser());
    }


    @Data
    public static class TokenInput
    {
        private String token;
    }

    @Data
    @AllArgsConstructor
    public static class LoginResult
    {
        private OAuthAction.ResponseAction action;
        private String token;
    }

    public static class UserInfoOutput
    {
        private final AuthUser authUser;

        public UserInfoOutput(AuthUser authUser) {
            this.authUser = authUser;
        }

        public String getUuid() {
            return authUser.getUuid();
        }

        public String getUsername() {
            return authUser.getUsername();
        }

        public String getNickname() {
            return authUser.getNickname();
        }

        public String getAvatar() {
            return authUser.getAvatar();
        }

        public String getBlog() {
            return authUser.getBlog();
        }

        public String getCompany() {
            return authUser.getCompany();
        }

        public String getEmail() {
            return authUser.getEmail();
        }

        public AuthUserGender getGender() {
            return authUser.getGender();
        }
    }

}
