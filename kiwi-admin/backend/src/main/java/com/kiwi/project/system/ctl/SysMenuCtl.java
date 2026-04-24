package com.kiwi.project.system.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.kiwi.common.tree.TreeNode;
import com.kiwi.common.tree.Tree;
import com.kiwi.project.system.dao.SysMenuDao;
import com.kiwi.project.system.entity.SysMenu;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/system/menu")
@Tag(name = "系统菜单", description = "菜单 CRUD 与树")
public class SysMenuCtl {

    private final SysMenuDao sysMenuDao;

    @Operation(operationId = "menu_get", summary = "按 id 获取菜单详情")
    @GetMapping("{id}")
    @SaCheckPermission("system:menu:view")
    @ResponseBody
    public SysMenu menu(@PathVariable("id") String id) {
        return this.sysMenuDao.findById(id).orElseThrow();
    }

    @Operation(operationId = "menu_tree", summary = "获取菜单树（从根节点展开）")
    @GetMapping()
    @SaCheckPermission("system:menu:view")
    @ResponseBody
    public List<TreeNode<SysMenu>> menuList() {
        List<SysMenu> menus = this.sysMenuDao.findAll();
        return Tree.build(menus).getByParentId("0");
    }

    @Operation(operationId = "menu_add", summary = "新增菜单")
    @PostMapping()
    @SaCheckPermission("system:menu:add")
    @ResponseBody
    public SysMenu addMenu(@RequestBody SysMenu sysMenu) {
        this.sysMenuDao.insert(sysMenu);
        return sysMenu;
    }

    @Operation(operationId = "menu_edit", summary = "按 id 修改菜单")
    @PutMapping("{id}")
    @SaCheckPermission("system:menu:update")
    @ResponseBody
    public SysMenu editMenu(@PathVariable("id") String id, @RequestBody SysMenu sysMenu) {
        sysMenu.setId(id);
        this.sysMenuDao.updateSelective(sysMenu);
        return this.sysMenuDao.findById(id).orElseThrow();
    }

    @Operation(operationId = "menu_delete", summary = "按 id 删除菜单")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:menu:delete")
    @ResponseBody
    public void deleteMenu(@PathVariable("id") String id) {
        this.sysMenuDao.deleteById(id);
    }
}
