package com.kiwi.project.tools.codegen.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import com.kiwi.project.tools.codegen.dao.GenFieldDao;
import com.kiwi.project.tools.codegen.entity.GenField;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    @SaCheckPermission("gen:field:view")
    @GetMapping("/{id}")
    public GenField getById(@PathVariable String id) {
        return genFieldDao.findById(id).orElseThrow();
    }

    @SaCheckPermission("gen:field:add")
    @PostMapping
    public GenField create(@RequestBody GenField genField) {
        genFieldDao.save(genField);
        return genField;
    }

    @SaCheckPermission("gen:field:edit")
    @PutMapping("/{id}")
    public GenField update(@PathVariable String id, @RequestBody GenField genField) {
        genField.setId(id);
        genFieldDao.updateSelective(genField);
        return genField;
    }

    @SaCheckPermission("gen:field:delete")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        genFieldDao.deleteById(id);
    }
}