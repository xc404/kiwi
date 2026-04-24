package com.kiwi.project.system.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.kiwi.project.system.dao.SysUserDao;
import com.kiwi.project.system.entity.SysUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 用户管理接口
 * CRUD
 */
@RestController
@RequestMapping("/system/user")
@Tag(name = "系统用户", description = "用户 CRUD")
public class SysUserCtl {
    @Autowired
    private SysUserDao sysUserDao;

    @Operation(operationId = "user_create", summary = "创建用户")
    @PostMapping
    @SaCheckPermission("sa:user:add")
    public SysUser create(@RequestBody SysUser user) {
        return sysUserDao.insert(user);
    }

    @Operation(operationId = "user_list", summary = "获取全部用户列表")
    @GetMapping
    @SaCheckPermission("sa:user:list")
    public List<SysUser> list() {
        return sysUserDao.findAll();
    }

    @Operation(operationId = "user_get", summary = "按 id 获取用户详情")
    @GetMapping("/{id}")
    @SaCheckPermission("sa:user:view")
    public Optional<SysUser> get(@PathVariable String id) {
        return sysUserDao.findById(id);
    }

    @Operation(operationId = "user_update", summary = "按 id 更新用户")
    @PutMapping("/{id}")
    @SaCheckPermission("sa:user:update")
    public SysUser update(@PathVariable("id") String id, @RequestBody SysUser user) {
        sysUserDao.updateSelective(user);
        return user;
    }

    @Operation(operationId = "user_delete", summary = "按 id 删除用户")
    @DeleteMapping("/{id}")
    @SaCheckPermission("sa:user:delete")
    public void delete(@PathVariable String id) {
        sysUserDao.deleteById(id);
    }
}
