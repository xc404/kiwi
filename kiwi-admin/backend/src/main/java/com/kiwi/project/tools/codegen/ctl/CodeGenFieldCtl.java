package com.kiwi.project.tools.codegen.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import com.kiwi.project.tools.codegen.dao.GenFieldDao;
import com.kiwi.project.tools.codegen.entity.GenField;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("tools/codegen/field")
@RequiredArgsConstructor
@Tag(name = "代码生成字段", description = "GenField CRUD")
public class CodeGenFieldCtl {

    private final GenFieldDao genFieldDao;

    @Data
    public static class SearchInput {
        @QueryField(value = "entityId", condition = QueryField.Type.EQ)
        private String entityId;
    }

    @Operation(operationId = "codeFld_listByTable", summary = "按表/实体 id 列出全部字段")
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


    @Operation(operationId = "codeFld_get", summary = "按 id 获取字段配置")
    @SaCheckPermission("gen:field:view")
    @GetMapping("/{id}")
    public GenField getById(@PathVariable String id) {
        return genFieldDao.findById(id).orElseThrow();
    }

    @Operation(operationId = "codeFld_create", summary = "新增代码生成字段")
    @SaCheckPermission("gen:field:add")
    @PostMapping
    public GenField create(@RequestBody GenField genField) {
        genFieldDao.save(genField);
        return genField;
    }

    @Operation(operationId = "codeFld_update", summary = "按 id 更新字段配置")
    @SaCheckPermission("gen:field:edit")
    @PutMapping("/{id}")
    public GenField update(@PathVariable String id, @RequestBody GenField genField) {
        genField.setId(id);
        genFieldDao.updateSelective(genField);
        return genField;
    }

    @Operation(operationId = "codeFld_delete", summary = "按 id 删除字段配置")
    @SaCheckPermission("gen:field:delete")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        genFieldDao.deleteById(id);
    }
}
