package com.cryo.oauth.service;

import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.request.AuthQQMiniProgramRequest;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class OAuthConfigService implements InitializingBean
{
//    public static final String OAUTH_CONFIG_FILE = "classpath:oauth/oauth_platform.json";

    @Value("${app.oauth.config_path}")
    private String oauth_config_file;

    private final Map<OAuthPlatform, OAuth2Config> authConfigMap = new HashMap<>();

    public OAuth2Config getAuthConfig(OAuthPlatform oAuthPlatform)
    {
        return this.authConfigMap.get(oAuthPlatform);
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        File file = ResourceUtils.getFile(oauth_config_file);
        Map<OAuthPlatform, OAuth2Config> configMap = JsonUtil.readMap(new FileInputStream(file), OAuthPlatform.class, OAuth2Config.class);
        this.authConfigMap.putAll(configMap);
    }
}
