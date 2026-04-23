package com.cryo.service.session;

import cn.dev33.satoken.stp.StpUtil;
import com.cryo.dao.GroupRepository;
import com.cryo.dao.UserRepository;
import com.cryo.model.user.Group;
import com.cryo.model.user.Role;
import com.cryo.model.user.User;
import com.cryo.service.PasswordService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionService
{
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    private final PasswordService passwordService;

    public SessionUser getSessionUser() {
        String id = StpUtil.getLoginId().toString();
        if( StringUtils.isBlank(id) ) {
            return null;
        }
        return Optional.ofNullable(StpUtil.getSession().get("user", () -> {
            User user = userRepository.findById(id).orElse(null);
            return user;
        })).map(u -> new SessionUser(u, StpUtil.getTokenValue())).orElse(null);
    }

    public SessionUser login(String email, String password) {

        User user = this.userRepository.findByEmail(email).orElse(null);
        if( user == null ) {
            return null;
        }
        if( !user.getIs_verified() ) {
            throw new RuntimeException("User not verified");
        }
        String encodePassword = passwordService.encodePassword(password);
        if( !user.getPassword().equals(encodePassword) ) {
            return null;
        }
        StpUtil.login(user.getId());
        return new SessionUser(user, StpUtil.getTokenValue());
    }

    public User loginV2(String email, String password) {

        User user = this.userRepository.findByEmail(email).orElse(null);
        if( user == null ) {
            return null;
        }
        String encodePassword = passwordService.encodePassword(password);
        if( !user.getPassword().equals(encodePassword) ) {
            return null;
        }
        return user;
    }

    private double getVolume(User user) {

        Group group = this.groupRepository.findByName(user.getUser_group()).orElse(null);
        if( group == null ) {
            return 0D;
        }
        return ((double) group.getVolume_in_bytes()) / (1024 ^ 4);
    }


    public boolean isAdmin() {

        return getSessionUser().getUser().getRole() == Role.ADMIN;
    }
}
