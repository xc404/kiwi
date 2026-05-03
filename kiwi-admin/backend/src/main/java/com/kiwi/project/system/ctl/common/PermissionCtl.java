package com.kiwi.project.system.ctl.common;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.permission.Permission;
import com.kiwi.framework.permission.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@SaCheckLogin
@RestController
@AllArgsConstructor
@Tag(name = "权限元数据", description = "系统权限定义列表")
public class PermissionCtl {
    private final PermissionService permissionService;

    @Operation(operationId = "perm_list", summary = "列出系统权限定义（元数据）")
    @GetMapping("common/permission")
    public List<Permission> getPermissions() {
        return permissionService.getPermissions();
    }
}
