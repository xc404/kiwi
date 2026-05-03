package com.kiwi.project.system.ctl.common;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.kiwi.common.tree.TreeNode;
import com.kiwi.common.tree.Tree;
import com.kiwi.framework.permission.PermissionService;
import com.kiwi.framework.session.SessionService;
import com.kiwi.framework.session.SessionUser;
import com.kiwi.project.system.dao.SysMenuDao;
import com.kiwi.project.system.entity.SysMenu;
import com.kiwi.project.system.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@RestController
@RequiredArgsConstructor
@Tag(name = "认证与当前用户", description = "登录、用户信息、菜单与权限")
public class AuthCtl {
    public static class LoginInput {
        public String userName;
        public String password;
    }

    @Data
    @AllArgsConstructor
    public static class LoginOutput {
        public final String token;
    }

    private final SessionService sessionService;
    private final MenuService menuService;
    private final PermissionService permissionService;
    private final SysMenuDao sysMenuDao;

    @PostMapping("/auth/signin")
    @ResponseBody
    public LoginOutput signin(@RequestBody LoginInput input) {
        String username = input.userName;
        this.sessionService.login(username, input.password);

        return new LoginOutput(StpUtil.getTokenValue());
    }

    @PostMapping("/auth/signout")
    @ResponseBody
    public void signout() {
        this.sessionService.logout();
    }

    @Operation(operationId = "auth_userInfo", summary = "获取当前登录用户信息")
    @GetMapping("/auth/userinfo")
    @SaCheckLogin
    @ResponseBody
    public SessionUser getUserInfo() {
        this.sessionService.refresh();
        return this.sessionService.getCurrentUser();
    }

    @Operation(operationId = "auth_menus", summary = "获取当前用户可见菜单树")
    @GetMapping("/auth/menus")
    @SaCheckLogin
    @ResponseBody
    public List<TreeNode<SysMenu>> getMenus() {
        SessionUser sessionUser = this.sessionService.getCurrentUser();

        List<SysMenu> visibleMenus = this.menuService.getVisibleMenus();
        if (sessionUser.isSuperUser()) {
            return Tree.build(visibleMenus).getByParentId(SysMenu.Root);
        }

        Set<String> menus = sessionService.getRoles().stream().map(role -> role.getMenuIds())
                .flatMap(menuIds -> menuIds.stream()).collect(Collectors.toSet());
        visibleMenus = visibleMenus.stream().filter(menu -> menus.contains(menu.getId())).toList();
        return Tree.build(visibleMenus).getByParentId(SysMenu.Root);
    }

    @Operation(operationId = "auth_permissions", summary = "获取当前登录用户权限码列表")
    @GetMapping("/auth/permissions")
    @SaCheckLogin
    @ResponseBody
    public List<String> getPermissions() {

        return this.sessionService.getPermissions();
    }
}
