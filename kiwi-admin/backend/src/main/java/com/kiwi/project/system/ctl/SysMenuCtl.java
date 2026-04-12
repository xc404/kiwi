package com.kiwi.project.system.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.ai.tool.annotation.Tool;
import com.kiwi.common.tree.TreeNode;
import com.kiwi.common.tree.Tree;
import com.kiwi.project.system.dao.SysMenuDao;
import com.kiwi.project.system.entity.SysMenu;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/system/menu")
public class SysMenuCtl
{

    private final SysMenuDao sysMenuDao;
//    private final SysMenuPermissionDao sysMenuPermissionDao;

    @Tool(name = "menu_get", description = "按 id 获取菜单详情。")
    @GetMapping("{id}")
    @SaCheckPermission("system:menu:view")
    @Operation(description = "菜单查看")
    @ResponseBody
    public SysMenu menu(@PathVariable("id") String id) {
        return this.sysMenuDao.findById(id).orElseThrow();
    }

    @Tool(name = "menu_tree", description = "获取菜单树（从根节点展开）。")
    @GetMapping()
    @SaCheckPermission("system:menu:view")
    @Operation(description = "菜单查看")
    @ResponseBody
    public List<TreeNode<SysMenu>> menuList() {
        List<SysMenu> menus = this.sysMenuDao.findAll();
        return Tree.build(menus).getByParentId("0");
    }

    @Tool(name = "menu_add", description = "新增菜单。")
    @PostMapping()
    @SaCheckPermission("system:menu:add")
    @Operation(description = "菜单添加")
    @ResponseBody
    public SysMenu addMenu(@RequestBody SysMenu sysMenu) {
        this.sysMenuDao.insert(sysMenu);
        return sysMenu;
    }

    @Tool(name = "menu_edit", description = "按 id 修改菜单。")
    @PutMapping("{id}")
    @SaCheckPermission("system:menu:update")
    @Operation(description = "菜单修改")
    @ResponseBody
    public SysMenu editMenu(@PathVariable("id") String id, @RequestBody SysMenu sysMenu) {
        sysMenu.setId(id);
        this.sysMenuDao.updateSelective(sysMenu);
        return this.sysMenuDao.findById(id).orElseThrow();
    }

    @Tool(name = "menu_delete", description = "按 id 删除菜单。")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:menu:delete")
    @Operation(description = "菜单删除")
    @ResponseBody
    public void deleteMenu(@PathVariable("id") String id) {
        this.sysMenuDao.deleteById(id);
    }

//    @GetMapping("{id}/permissions")
//    @SaCheckPermission("system:menu:permission")
//    @Operation(description = "菜单权限管理")
//    @ResponseBody
//    public List<SysMenuPermission> menuPermissions(@PathVariable("id") String id) {
//        return this.sysMenuPermissionDao.findByMenuId(id);
//    }

}
