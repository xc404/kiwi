package com.kiwi.project.system.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.kiwi.project.system.dao.SysUserDao;
import com.kiwi.project.system.entity.SysUser;
import io.swagger.v3.oas.annotations.Operation;
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
public class SysUserCtl {
    @Autowired
    private SysUserDao sysUserDao;
    /**
     * 创建用户
     */
    @PostMapping
    @Operation(summary = "创建用户")
    @SaCheckPermission("sa:user:add")
    public SysUser create(@RequestBody SysUser user) {
        return sysUserDao.insert(user);
    }

    /**
     * 获取用户列表
     */
    @GetMapping
    @Operation(summary = "获取用户列表")
    @SaCheckPermission("sa:user:list")
    public List<SysUser> list() {
        return sysUserDao.findAll();
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取用户详情")
    @SaCheckPermission("sa:user:view")
    public Optional<SysUser> get(@PathVariable String id) {
        return sysUserDao.findById(id);
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新用户")
    @SaCheckPermission("sa:user:update")
    public SysUser update(@PathVariable("id")String id ,@RequestBody SysUser user) {
         sysUserDao.updateSelective(user);
         return user;
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    @SaCheckPermission("sa:user:delete")
    public void delete(@PathVariable String id) {
        sysUserDao.deleteById(id);
    }
}
