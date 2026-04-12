package com.kiwi.project.system.ctl.common;

import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.ai.tool.annotation.Tool;
import com.kiwi.framework.permission.Permission;
import com.kiwi.framework.permission.PermissionService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@SaCheckLogin
@RestController
@AllArgsConstructor
public class PermissionCtl
{
    private final PermissionService permissionService;

    @Tool(name = "perm_list", description = "列出系统权限定义（元数据）。")
    @GetMapping("common/permission")
    public List<Permission> getPermissions() {
        return permissionService.getPermissions();
    }
}
