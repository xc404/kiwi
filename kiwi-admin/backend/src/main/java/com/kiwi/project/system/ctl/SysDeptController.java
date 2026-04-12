package com.kiwi.project.system.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import org.springframework.ai.tool.annotation.Tool;
import com.kiwi.project.system.dao.SysDeptDao;
import com.kiwi.project.system.entity.SysDept;
import io.swagger.v3.oas.annotations.Operation;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门Controller
 *
 * @author kiwi
 * @date Tue Dec 30 08:44:34 CST 2025
 */
@RestController
@RequestMapping("/system/dept")
public class SysDeptController
{
    @Autowired
    private SysDeptDao sysDeptDao;

    /**
     * 查询部门列表
     */
    @SaCheckPermission("system:dept:view")
    @GetMapping("")
    @Operation(description = "查询部门列表")
    @ResponseBody
    public Page<SysDept> page(SearchInput searchInput, Pageable pageable) {
        return this.sysDeptDao.findBy(QueryParams.of(searchInput), pageable);
    }

    @Tool(
            name = "dept_aiSearch",
            description = "分页查询部门。deptName 模糊匹配；page 从 0 开始，size 默认 20、最大 100。")
    @SaCheckPermission("system:dept:view")
    public Page<SysDept> aiSearch(String deptName, Integer page, Integer size) {
        SearchInput si = new SearchInput();
        si.setDeptName(deptName);
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return page(si, PageRequest.of(p, s));
    }

    /**
     * 查询部门列表
     */
    @Tool(name = "dept_children", description = "查询某部门 id 下的子部门列表。")
    @SaCheckPermission("system:dept:view")
    @GetMapping("{id}/children")
    @Operation(description = "查询部门列表")
    @ResponseBody
    public List<SysDept> children(@PathVariable("id") String id) {
        return this.sysDeptDao.findByParentId(id);
    }


    /**
     * 获取部门详细信息
     */
    @Tool(name = "dept_get", description = "按 id 获取部门详情。")
    @SaCheckPermission("system:dept:view")
    @GetMapping(value = "/{id}")
    @Operation(description = "获取部门详细信息")
    @ResponseBody
    public SysDept get(@PathVariable("id") String id) {
        return this.sysDeptDao.findById(id).orElseThrow();
    }

    /**
     * 新增部门
     */
    @Tool(name = "dept_add", description = "新增部门。")
    @PostMapping()
    @SaCheckPermission("system:dept:add")
    @Operation(description = "新增部门")
    @ResponseBody
    public SysDept add(@RequestBody SysDept sysDept) {
        this.sysDeptDao.insert(sysDept);
        return sysDept;
    }

    /**
     * 修改部门
     */
    @Tool(name = "dept_edit", description = "按 id 修改部门。")
    @PutMapping("{id}")
    @SaCheckPermission("system:dept:update")
    @Operation(description = "修改部门")
    @ResponseBody
    public SysDept edit(@PathVariable("id") String id, @RequestBody SysDept sysDept) {
        sysDept.setId(id);
        this.sysDeptDao.updateSelective(sysDept);
        return this.sysDeptDao.findById(id).orElseThrow();
    }

    /**
     * 删除部门
     */
    @Tool(name = "dept_delete", description = "按 id 列表批量删除部门（路径参数为逗号分隔或多个 id，与 HTTP 一致）。")
    @DeleteMapping("{ids}")
    @SaCheckPermission("system:dept:delete")
    @Operation(description = "删除部门")
    @ResponseBody
    public void delete(@PathVariable List<String> ids) {
        this.sysDeptDao.deleteAllById(ids);
    }


    @Data
    public static class SearchInput
    {
        @QueryField(value = "deptName", condition = QueryField.Type.LIKE)
        private String deptName;

    }

}
