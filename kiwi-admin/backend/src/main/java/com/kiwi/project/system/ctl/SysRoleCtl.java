package com.kiwi.project.system.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import org.springframework.ai.tool.annotation.Tool;
import com.kiwi.project.system.dao.SysRoleDao;
import com.kiwi.project.system.entity.SysRole;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 角色管理接口
 * CRUD + 权限
 */
@RestController
@RequestMapping("/system/role")
public class SysRoleCtl {
    @Autowired
    private SysRoleDao sysRoleDao;

    /**
     * 创建角色
     */
    @Tool(name = "role_create", description = "创建角色。请求体为角色实体 JSON。")
    @PostMapping
    @Operation(summary = "创建角色")
    @SaCheckPermission("sa:role:add")
    public SysRole create(@RequestBody SysRole role) {
        return sysRoleDao.insert(role);
    }

    /**
     * 获取角色列表
     */
    @GetMapping
    @Operation(summary = "获取角色列表")
    @SaCheckPermission("sa:role:view")
    public Page<SysRole> page(QueryInput queryInput, Pageable pageable) {
        return sysRoleDao.findBy(QueryParams.of(queryInput),pageable);
    }

    @Tool(
            name = "role_aiSearch",
            description = "分页查询角色列表。name 支持模糊；page 从 0 开始，size 默认 20、最大 100。")
    @SaCheckPermission("sa:role:view")
    public Page<SysRole> aiSearch(String name, Integer page, Integer size) {
        QueryInput q = new QueryInput();
        q.name = name;
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return page(q, PageRequest.of(p, s));
    }

    /**
     * 获取角色详情
     */
    @Tool(name = "role_get", description = "按 id 获取角色详情。")
    @GetMapping("/{id}")
    @Operation(summary = "获取角色详情")
    @SaCheckPermission("sa:role:view")
    public Optional<SysRole> get(@PathVariable String id) {
        return sysRoleDao.findById(id);
    }

    /**
     * 更新角色
     */
    @Tool(name = "role_update", description = "按 id 更新角色。")
    @PutMapping("{id}")
    @Operation(summary = "更新角色")
    @SaCheckPermission("sa:role:update")
    public SysRole update(@PathVariable("id") String id, @RequestBody SysRole role) {
        sysRoleDao.updateSelective(role);
        return role;
    }

    /**
     * 删除角色
     */
    @Tool(name = "role_delete", description = "按 id 删除角色。")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色")
    @SaCheckPermission("sa:role:delete")
    public void delete(@PathVariable String id) {
        sysRoleDao.deleteById(id);
    }

    public static class QueryInput {
        @QueryField(value = "name", condition = QueryField.Type.LIKE)
        public String name;
    }

}
