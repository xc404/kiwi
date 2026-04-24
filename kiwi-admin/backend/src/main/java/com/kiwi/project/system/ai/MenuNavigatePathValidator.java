package com.kiwi.project.system.ai;

import com.kiwi.framework.session.SessionService;
import com.kiwi.framework.session.SessionUser;
import com.kiwi.project.system.entity.SysMenu;
import com.kiwi.project.system.entity.SysRole;
import com.kiwi.project.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 校验应用内跳转路径：须与 {@code /auth/menus} 所依据的当前用户可见菜单 {@link SysMenu#getPath()} 一致（规范化后比较）。
 */
@Component
@RequiredArgsConstructor
public class MenuNavigatePathValidator {

    /**
     * 与前端主布局下路由一致（不再包含 {@code /default} 前缀），禁止外链与路径穿越。
     */
    private static final Pattern SAFE_APP_PATH = Pattern.compile("^/[a-zA-Z0-9_.:-]+(/[a-zA-Z0-9_.:-]+)*$");

    private final MenuService menuService;
    private final SessionService sessionService;

    public Optional<String> validate(String path) {
        if (path == null || path.isBlank()) {
            return Optional.of("routePath 不能为空。");
        }
        String trimmed = path.trim();
        if (!SAFE_APP_PATH.matcher(trimmed).matches()) {
            return Optional.of("routePath 格式不合法，须为应用内路径（例如 /system/dict），且与菜单中配置的路由一致。");
        }
        if (!isListedInUserMenus(trimmed)) {
            return Optional.of("routePath 与当前用户可见菜单中的路由不一致，请先调用 auth_menus 查看各菜单的 path 字段。");
        }
        return Optional.empty();
    }

    private boolean isListedInUserMenus(String path) {
        SessionUser user = sessionService.getCurrentUser();
        if (user == null) {
            return false;
        }
        String canonical = canonicalPath(path);
        for (SysMenu m : menusForUser(user)) {
            if (m.getPath() == null || m.getPath().isBlank()) {
                continue;
            }
            if (!isNavigableMenuType(m.getMenuType())) {
                continue;
            }
            if (canonicalPath(m.getPath()).equals(canonical)) {
                return true;
            }
        }
        return false;
    }

    private List<SysMenu> menusForUser(SessionUser user) {
        var visible = menuService.getVisibleMenus();
        if (user.isSuperUser()) {
            return visible;
        }
        var roles = sessionService.getRoles();
        if (roles == null) {
            return List.of();
        }
        Set<String> allowedIds = roles.stream()
                .filter(Objects::nonNull)
                .map(SysRole::getMenuIds)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        return visible.stream().filter(m -> allowedIds.contains(m.getId())).toList();
    }

    private static boolean isNavigableMenuType(String menuType) {
        return "M".equals(menuType) || "C".equals(menuType);
    }

    private static String canonicalPath(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        s = s.replaceAll("/+", "/");
        if (!s.startsWith("/")) {
            s = "/" + s;
        }
        if (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
