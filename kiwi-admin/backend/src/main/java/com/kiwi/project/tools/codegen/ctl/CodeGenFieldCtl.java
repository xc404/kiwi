package com.kiwi.project.tools.codegen.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.ai.tool.annotation.Tool;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import com.kiwi.project.tools.codegen.dao.GenFieldDao;
import com.kiwi.project.tools.codegen.entity.GenField;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("tools/codegen/field")
@RequiredArgsConstructor
public class CodeGenFieldCtl
{

    private final GenFieldDao genFieldDao;

    @Data
    public static class SearchInput
    {

        @QueryField(value = "entityId", condition = QueryField.Type.EQ)
        private String entityId;
    }

    @Tool(name = "codeFld_listByTable", description = "按表/实体 id 列出全部字段。")
    @SaCheckPermission("gen:field:list")
    @GetMapping("byTable/{tableId}")
    public List<GenField> listByTableId(@PathVariable String tableId) {
        GenField genField = new GenField();
        genField.setEntityId(tableId);
        QueryParams params = QueryParams.of(genField);
        return this.genFieldDao.findBy(params);
    }


    @SaCheckPermission("gen:field:list")
    @GetMapping("")
    public Page<GenField> page(SearchInput searchInput, Pageable pageable) {
        QueryParams params = QueryParams.of(searchInput);
        return this.genFieldDao.findBy(params, pageable);
    }

    @Tool(
            name = "codeFld_aiPage",
            description = "分页查询代码生成字段。entityId 必填；page 从 0 开始，size 默认 20、最大 100。")
    @SaCheckPermission("gen:field:list")
    public Page<GenField> aiPage(String entityId, Integer page, Integer size) {
        SearchInput si = new SearchInput();
        si.setEntityId(entityId);
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return page(si, PageRequest.of(p, s));
    }

    @Tool(name = "codeFld_get", description = "按 id 获取字段配置。")
    @SaCheckPermission("gen:field:view")
    @GetMapping("/{id}")
    public GenField getById(@PathVariable String id) {
        return genFieldDao.findById(id).orElseThrow();
    }

    @Tool(name = "codeFld_create", description = "新增代码生成字段。")
    @SaCheckPermission("gen:field:add")
    @PostMapping
    public GenField create(@RequestBody GenField genField) {
        genFieldDao.save(genField);
        return genField;
    }

    @Tool(name = "codeFld_update", description = "按 id 更新字段配置。")
    @SaCheckPermission("gen:field:edit")
    @PutMapping("/{id}")
    public GenField update(@PathVariable String id, @RequestBody GenField genField) {
        genField.setId(id);
        genFieldDao.updateSelective(genField);
        return genField;
    }

    @Tool(name = "codeFld_delete", description = "按 id 删除字段配置。")
    @SaCheckPermission("gen:field:delete")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        genFieldDao.deleteById(id);
    }
}