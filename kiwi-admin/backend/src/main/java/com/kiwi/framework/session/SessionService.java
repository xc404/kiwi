package com.kiwi.framework.session;

import cn.dev33.satoken.stp.StpUtil;
import com.kiwi.framework.permission.PermissionService;
import com.kiwi.framework.security.PasswordService;
import com.kiwi.project.system.dao.SysRoleDao;
import com.kiwi.project.system.dao.SysUserDao;
import com.kiwi.project.system.entity.SysRole;
import com.kiwi.project.system.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService
{
    public static final String UserKey = "_session_user";
    public static final String PermissionsKey = "_session_permission";
    public static final String RolesKey = "_session_role";
    //    public static final String UserKey = "session_user";
    private final SysUserDao userDao;
    private final SysRoleDao sysRoleDao;
    private final PasswordService passwordService;
    private final PermissionService permissionService;

    public SessionUser getCurrentUser() {
        String id = StpUtil.getLoginId().toString();
        if( StringUtils.isBlank(id) ) {
            return null;
        }
        try {
            return StpUtil.getSession().get(UserKey, () -> {
                SysUser user = userDao.findById(id).orElse(null);
                return new SessionUser(user);
            });
        } catch( Exception e ) {
            log.error(e.getMessage(), e);
            StpUtil.getSession().delete(UserKey);
            return StpUtil.getSession().get(UserKey, () -> {
                SysUser user = userDao.findById(id).orElse(null);
                return new SessionUser(user);
            });
        }

    }


    public List<SysRole> getRoles() {
        String id = StpUtil.getLoginId().toString();
        if( StringUtils.isBlank(id) ) {
            return null;
        }
        SessionUser currentUser = getCurrentUser();

        return StpUtil.getSession().get(RolesKey, () -> {
            List<String> roleIds = currentUser.getRoleIds();
            if( CollectionUtils.isNotEmpty(roleIds) ) {
                List<SysRole> sysRoles = this.sysRoleDao.findBy(Query.query(Criteria.where("_id").in(roleIds)));
                return sysRoles;
            }
            return List.of();
        });
    }

    public List<String> getPermissions() {
        String id = StpUtil.getLoginId().toString();
        if( StringUtils.isBlank(id) ) {
            return null;
        }
        SessionUser currentUser = getCurrentUser();

        return StpUtil.getSession().get(PermissionsKey, () -> {
            if( currentUser.isSuperUser() ) {
                return permissionService.getPermissions().stream().map(permission -> permission.getKey()).toList();
            }
            List<SysRole> sysRoles = getRoles();
            return sysRoles.stream().map(role -> role.getPermissions())
                    .flatMap(permissions -> permissions.stream()).toList();
        });
    }


    public void login(String userName, String password) {

        SysUser user = this.userDao.findByUsername(userName).orElse(null);
        if( user == null ) {
            throw new RuntimeException("Invalid username or password");
        }
        String encodePassword = passwordService.encodePassword(password);
        if( !user.getPassword().equals(encodePassword) ) {
            throw new RuntimeException("Invalid username or password");
        }

        StpUtil.login(user.getId());
    }

    public void refresh() {
        StpUtil.getSession().clear();
    }

    public void logout() {
        StpUtil.logout();
    }
}
