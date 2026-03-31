package com.kiwi.project.system.service;

import com.kiwi.common.tree.TreeNode;
import com.kiwi.common.tree.Tree;
import com.kiwi.project.system.spi.Refreshable;
import com.kiwi.project.system.dao.SysDeptDao;
import com.kiwi.project.system.entity.SysDept;
import com.kiwi.project.system.spi.TreeProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeptService implements Refreshable, InitializingBean, TreeProvider<SysDept>
{
    private final SysDeptDao sysDeptDao;

    @Getter
    private List<SysDept> visibleDepts;

    private List<SysDept> allDepts;
    private Tree<SysDept> allDeptTree;

    @Override
    public void refresh() {
        this.allDepts = this.sysDeptDao.findAll();

        this.visibleDepts = allDepts.stream().filter(m -> m.enabled()).toList();

        this.allDeptTree = Tree.build(allDepts);

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.refresh();
    }


    @Override
    public String group() {
        return "sys-dept";
    }

    @Override
    public List<SysDept> getChildren(String parentId, Map<String, Object> params) {
        return this.sysDeptDao.findByParentId(parentId);
    }

    @Override
    public List<TreeNode<SysDept>> getTree(String parentId, Map<String, Object> params) {
        return this.allDeptTree.getByParentId(parentId);
    }
}
