package com.kiwi.project.system.service;

import com.kiwi.common.tree.TreeNode;
import com.kiwi.common.tree.Tree;
import com.kiwi.project.system.spi.Refreshable;
import com.kiwi.project.system.dao.SysMenuDao;
import com.kiwi.project.system.entity.SysMenu;
import com.kiwi.project.system.spi.TreeProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MenuService implements Refreshable, InitializingBean, TreeProvider<SysMenu>
{
    private final SysMenuDao sysMenuDao;

    @Getter
    private List<SysMenu> visibleMenus;

    private List<SysMenu> allMenus;
    private Tree<SysMenu> allMenuTree;

    @Override
    public void refresh() {
        this.allMenus = this.sysMenuDao.findAll();

        this.visibleMenus = allMenus.stream().filter(m -> m.isVisible()).toList();

        this.allMenuTree = Tree.build(allMenus);

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.refresh();
    }


    @Override
    public String group() {
        return "menus";
    }

    @Override
    public List<SysMenu> getChildren(String parentId, Map<String, Object> params) {
        return this.sysMenuDao.findByParentId(parentId);
    }

    @Override
    public List<TreeNode<SysMenu>> getTree(String parentId, Map<String, Object> params) {
        return this.allMenuTree.getByParentId(parentId);
    }
}
