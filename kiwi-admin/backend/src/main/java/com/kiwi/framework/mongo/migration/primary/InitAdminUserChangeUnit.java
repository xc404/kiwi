package com.kiwi.framework.mongo.migration.primary;

import com.kiwi.framework.mongo.migration.MongoInitAdminProperties;
import com.kiwi.framework.security.PasswordService;
import com.kiwi.project.system.dao.SysUserDao;
import com.kiwi.project.system.entity.SysRole;
import com.kiwi.project.system.entity.SysUser;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

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
            MongoInitAdminProperties initAdmin) {
        this.sysUserDao = sysUserDao;
        this.passwordService = passwordService;
        this.adminUsername = initAdmin.getAdminUsername();
        this.adminPassword = initAdmin.getAdminPassword();
        this.adminNickName = initAdmin.getAdminNickName();
    }

    @Execution
    public void execute() {
        if (StringUtils.isBlank(adminPassword)) {
            log.warn(
                    "Skip admin user migration: kiwi.mongodb.init.admin-password / KIWI_MONGODB_INIT_ADMIN_PASSWORD is blank");
            return;
        }
        if (sysUserDao.findByUsername(adminUsername).isPresent()) {
            log.info("Admin user already exists, skip migration: {}", adminUsername);
            return;
        }
        SysUser user = new SysUser();
        user.setId(adminUsername);
        user.setUserName(adminUsername);
        user.setNickName(adminNickName);
        user.setPassword(passwordService.encodePassword(adminPassword));
        user.setStatus("0");
        user.setDelFlag("0");
        user.setDeptId("0");
        user.setEmail("admin@example.com");
        user.setRoleIds(List.of(SysRole.Admin));
        sysUserDao.save(user);
        log.info("Created initial admin user: {}", adminUsername);
    }

    @RollbackExecution
    public void rollback() {
        sysUserDao.findByUsername(adminUsername).ifPresent(user -> {
            sysUserDao.delete(user);
            log.info("Rolled back initial admin user: {}", adminUsername);
        });
    }
}
