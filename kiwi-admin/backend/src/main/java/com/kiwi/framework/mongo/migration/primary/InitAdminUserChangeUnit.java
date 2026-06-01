package com.kiwi.framework.mongo.migration.primary;

import com.kiwi.framework.security.PasswordService;
import com.kiwi.project.system.dao.SysUserDao;
import com.kiwi.project.system.entity.SysRole;
import com.kiwi.project.system.entity.SysUser;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@ChangeUnit(id = "20250601-001-init-admin-user", order = "001", author = "kiwi")
@Slf4j
public class InitAdminUserChangeUnit {

    private final SysUserDao sysUserDao;
    private final PasswordService passwordService;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminNickName;

    public InitAdminUserChangeUnit(
            SysUserDao sysUserDao,
            PasswordService passwordService,
            @Value("${kiwi.mongodb.init.admin-username}") String adminUsername,
            @Value("${kiwi.mongodb.init.admin-password:}") String adminPassword,
            @Value("${kiwi.mongodb.init.admin-nick-name}") String adminNickName) {
        this.sysUserDao = sysUserDao;
        this.passwordService = passwordService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminNickName = adminNickName;
    }

    @Execution
    public void execute() {
        if (StringUtils.isBlank(adminPassword)) {
            log.warn(
                    "Skip admin user migration: kiwi.mongodb.init.admin-password / KIWI_INIT_ADMIN_PASSWORD is blank");
            return;
        }
        if (sysUserDao.findByUsername(adminUsername).isPresent()) {
            log.info("Admin user already exists, skip migration: {}", adminUsername);
            return;
        }
        SysUser user = new SysUser();
        user.setUserName(adminUsername);
        user.setNickName(adminNickName);
        user.setPassword(passwordService.encodePassword(adminPassword));
        user.setStatus("0");
        user.setDelFlag("0");
        user.setRoleIds(List.of(SysRole.Admin));
        sysUserDao.save(user);
        log.info("Created initial admin user: {}", adminUsername);
    }
}
