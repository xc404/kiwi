package com.cryo.model.user;

import com.cryo.common.model.DataEntity;
import com.cryo.oauth.service.OAuthPlatform;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.Data;
import lombok.EqualsAndHashCode;
import me.zhyd.oauth.model.AuthUser;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


@EqualsAndHashCode(callSuper = true)
@Document("user")
@Data
public class User extends DataEntity
{
    @Deprecated
    private String first_name;
    @Deprecated
    private String last_name;
    private String email;
    private String name;
    @Hidden
    @JsonIgnore
    private String password;
    @Deprecated
    private String user_group;
    private String group_id;    // 关联 Group._id
    private Boolean is_verified;
    @Deprecated
    private String role;        // 旧字段，存 role_name，后续用 role_id 替代
    private int role_id;        // 关联 Role._id
    private String default_dir;
    private String sys_username;
    private boolean migrated;
    private String tags;
    private String cryosparcUserId;

    public String getSys_username() {
        return Optional.ofNullable(sys_username).orElse(user_group);
    }

    @Hidden
    @JsonIgnore
    private Map<OAuthPlatform, AuthUser> oauth_users;

    public void addOAuthUser(OAuthPlatform platform, AuthUser authUser) {
        if( oauth_users == null ) {
            oauth_users = new HashMap<>();
        }

        /*
         * if user already has oauth user for this platform, throw an error
         */
        if( oauth_users.containsKey(platform) ) {
            throw new RuntimeException("user already has oauth user for " + platform);
        }
        oauth_users.put(platform, authUser);
    }

    public AuthUser getOAuthUser(OAuthPlatform platform) {
        if( oauth_users == null ) {
            return null;
        }
        return oauth_users.get(platform);
    }


    public String getName() {
        if( oauth_users != null && oauth_users.get(OAuthPlatform.Itech) != null ) {
            return oauth_users.get(OAuthPlatform.Itech).getUsername();
        }
        if( name != null && !name.isEmpty() ) {
            return name;
        }
        return first_name + " " + last_name;
    }

    public String getItechId() {
        if( oauth_users != null && oauth_users.get(OAuthPlatform.Itech) != null ) {
            return oauth_users.get(OAuthPlatform.Itech).getUuid();
        }
        return "";
    }

    public String getTags() {
        return Optional.ofNullable(tags).orElse("");
    }

    public int getRole_id() {
        return Optional.ofNullable(role_id).orElse(0);
    }

}
