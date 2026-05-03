package com.cryo.service;

import com.cryo.dao.UserRepository;
import com.cryo.model.user.User;
import com.cryo.oauth.service.OAuthPlatform;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService
{

    private final UserRepository userRepository;

    public Optional<User> findByAuthUserId(OAuthPlatform platform, String uuid) {
        List<User> users = this.userRepository.findByQuery(Query.query(Criteria.where("oauth_users." + platform + ".uuid").is(uuid)));
        return !users.isEmpty() ? Optional.of(users.get(0)) : Optional.empty();
    }

    public Page<User> findByRoleId(int roleId, Pageable pageable) {
        return userRepository.findByQuery(
                Query.query(Criteria.where("role_id").is(roleId)), pageable);
    }
}
