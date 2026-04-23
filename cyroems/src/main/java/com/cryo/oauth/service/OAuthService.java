package com.cryo.oauth.service;

import com.cryo.common.utils.CacheUtils;
import com.cryo.dao.UserRepository;
import com.cryo.model.user.User;
import com.cryo.oauth.platform.ItechOAuthRequest;
import com.cryo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.AuthRequestBuilder;
import me.zhyd.oauth.config.AuthDefaultSource;
import me.zhyd.oauth.enums.AuthResponseStatus;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.UrlBuilder;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthService
{
    private static final Map<OAuthPlatform, Class> oAuth2RequestMap = new HashMap<>();

    static {
        oAuth2RequestMap.put(OAuthPlatform.Itech, ItechOAuthRequest.class);
    }

    private final OAuthConfigService oAuthConfigService;

    private final UserRepository userRepository;

    private final UserService userService;
//    private boolean autoBindUser     = true;

    public String oauth(OAuthInput authInput) {
        try {
            OAuth2Config authConfig = this.oAuthConfigService.getAuthConfig(authInput.getPlatform());
            if( authConfig == null ) {
                throw new RuntimeException("Invalid platform");
            }
            AuthRequest oAuth2Request = getOAuth2Request(authConfig);
            String state = CacheUtils.put(authInput);
            return oAuth2Request.authorize(state);
        } catch( Exception e ) {
            log.error(e.getMessage(), e);
            return backToClient(authInput, e);
        }
    }


    public OAuthCompleteResult complete(AuthCallback callback) {
        String state = callback.getState();
        OAuthInput o = CacheUtils.get(state);
        if( o == null ) {
            throw new RuntimeException("Invalid state");
        }
        OAuth2Config authConfig = this.oAuthConfigService.getAuthConfig(o.getPlatform());
        AuthRequest oAuth2Request = getOAuth2Request(authConfig);

        AuthResponse<AuthUser> userAuthResponse = oAuth2Request.login(callback);
//        AuthToken accessToken = oAuth2Request.getAccessToken(callback);
//
//        AuthUser userInfo = oAuth2Request.getUserInfo(accessToken);

        AuthUser userInfo = userAuthResponse.getData();
        if( userInfo == null ) {
            throw new AuthException(AuthResponseStatus.NOT_IMPLEMENTED.getCode(), userAuthResponse.getMsg());
        }
        User user = getUser(o.getPlatform(), userInfo);

        return new OAuthCompleteResult(o, userInfo, user);
    }

    public String backToClient(OAuthInput authInput, Exception e) {
        return UrlBuilder.fromBaseUrl(authInput.getReturnUrl()).queryParam("error", e.getMessage()).queryParam("requestAction", authInput.getAction()).build();
    }

    public String backToClient(OAuthInput authInput, String token) {
        return UrlBuilder.fromBaseUrl(authInput.getReturnUrl()).queryParam("token", token).queryParam("requestAction", authInput.getAction()).build();
    }

    public AuthRequest getOAuth2Request(OAuth2Config oAuth2Config) {

        AuthRequestBuilder authRequestBuilder = AuthRequestBuilder.builder().source(oAuth2Config.getName()).authConfig(oAuth2Config);

        AuthDefaultSource source = EnumUtils.getEnumIgnoreCase(AuthDefaultSource.class, oAuth2Config.getName());
        if( source == null ) {

            Class aClass = oAuth2RequestMap.get(OAuthPlatform.valueOf(oAuth2Config.getName()));
            if( aClass != null ) {
                oAuth2Config.setTargetClass(aClass);
            }
            authRequestBuilder.extendSource(oAuth2Config);
        }
        return authRequestBuilder.build();
    }

    public User getUser(OAuthPlatform platform, AuthUser authUser) {
        String uuid = authUser.getUuid();
        User user = this.userService.findByAuthUserId(platform, uuid).orElse(null);
        if( user == null && authUser.getEmail() != null ) {
            user = this.userRepository.findByEmail(authUser.getEmail()).orElse(null);
//            if(user != null && autoBindUser){
//                user.addOAuthUser(platform, authUser);
//            }
        }
        return user;
    }

}
