package com.cryo.service.migration;

import com.cryo.dao.GroupRepository;
import com.cryo.dao.RoleRepository;
import com.cryo.dao.UserRepository;
import com.cryo.model.user.Group;
import com.cryo.model.user.Role;
import com.cryo.model.user.User;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserMigrationService
{
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final GroupRepository groupRepository;

    /**
     * 根据 User.role (role_name string) 匹配 Role 表，写入 role_id
     */
    public MigrationResult migrateUserRole() {
        log.info("[migration] start migrating user role_id");
        List<User> allUsers = userRepository.findAll();
        Map<String, Role> roleByName = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getRole_name, r -> r, (a, b) -> a));

        int updated = 0, skipped = 0;
        for (User user : allUsers) {
            if (user.getRole_id() != 0) continue;
            String roleName = user.getRole();
            if (roleName == null) {
                skipped++;
                continue;
            }
            Role role = roleByName.get(roleName);
            if (role != null) {
                user.setRole_id(role.getRole_id());
                userRepository.save(user);
                updated++;
            } else {
                log.warn("[migration] no role found for role_name={} userId={}", roleName, user.getId());
                skipped++;
            }
        }
        log.info("[migration] role migration done. updated={} skipped={}", updated, skipped);
        return new MigrationResult(allUsers.size(), updated, skipped);
    }

    /**
     * 根据 User.user_group (group_name string) 匹配 Group 表，写入 group_id
     */
    public MigrationResult migrateUserGroup() {
        log.info("[migration] start migrating user group_id");
        List<User> allUsers = userRepository.findAll();
        Map<String, Group> groupByName = groupRepository.findAll().stream()
                .filter(g -> g.getGroup_name() != null)
                .collect(Collectors.toMap(Group::getGroup_name, g -> g, (a, b) -> a));

        int updated = 0, skipped = 0;
        for (User user : allUsers) {
            if (user.getGroup_id() != null) continue;
            String groupName = user.getUser_group();
            if (groupName == null) {
                skipped++;
                continue;
            }
            Group group = groupByName.get(groupName);
            if (group != null) {
                user.setGroup_id(group.getId());
                userRepository.save(user);
                updated++;
            } else {
                log.warn("[migration] no group found for group_name={} userId={}", groupName, user.getId());
                skipped++;
            }
        }
        log.info("[migration] group migration done. updated={} skipped={}", updated, skipped);
        return new MigrationResult(allUsers.size(), updated, skipped);
    }

    @Data
    public static class MigrationResult
    {
        private final int total;
        private final int updated;
        private final int skipped;
    }
}
