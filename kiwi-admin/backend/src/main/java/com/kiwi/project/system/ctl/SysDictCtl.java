package com.kiwi.project.system.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import com.kiwi.project.system.dao.SysDictDao;
import com.kiwi.project.system.dao.SysDictGroupDao;
import com.kiwi.project.system.entity.SysDict;
import com.kiwi.project.system.entity.SysDictGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/system/dict")
@Tag(name = "系统字典", description = "字典组与字典项维护")
public class SysDictCtl {
    private final SysDictGroupDao sysDictGroupDao;

    private final SysDictDao sysDictDao;

    public static class SearchInput {
        @QueryField(value = "groupCode", condition = QueryField.Type.LIKE)
        public String groupCode;
        @QueryField(value = "groupName", condition = QueryField.Type.LIKE)
        public String groupName;
    }

    @GetMapping("/group")
    @SaCheckPermission("system:dict:view")
    @ResponseBody
    public Page<SysDictGroup> listGroup(SearchInput searchInput, Pageable pageable) {
        return this.sysDictGroupDao.findBy(QueryParams.of(searchInput), pageable);
    }

    @Operation(
            operationId = "dict_aiSearchDictGroups",
            summary = "分页查询字典分类列表",
            description = "groupCode、groupName 支持模糊匹配；page 从 0 开始，size 默认 20、最大 100。")
    @GetMapping("/group/ai-page")
    @SaCheckPermission("system:dict:view")
    @ResponseBody
    public Page<SysDictGroup> aiSearchDictGroups(
            @RequestParam(required = false) String groupCode,
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        SearchInput searchInput = new SearchInput();
        searchInput.groupCode = groupCode;
        searchInput.groupName = groupName;
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return listGroup(searchInput, PageRequest.of(p, s));
    }

    @Operation(
            operationId = "dict_addDictGroup",
            summary = "新增字典分类（字典组）",
            description = "请求体字段与系统管理界面一致，如 groupCode、groupName、status、remark。")
    @SaCheckPermission("system:dict:edit")
    @PostMapping("/group")
    @ResponseBody
    public SysDictGroup addDictGroup(@RequestBody SysDictGroup sysDict) {
        this.sysDictGroupDao.insert(sysDict);
        return sysDict;
    }

    @SaCheckPermission("system:dict:edit")
    @PutMapping("/group/{id}")
    @ResponseBody
    public SysDictGroup updateDictGroup(@PathVariable("id") String id, @RequestBody SysDictGroup sysDict) {
        sysDict.setGroupCode(id);
        this.sysDictGroupDao.updateSelective(sysDict);
        return sysDict;
    }

    @SaCheckPermission("system:dict:delete")
    @DeleteMapping("/group/{id}")
    @ResponseBody
    public void deleteDictGroup(@PathVariable("id") String id) {
        this.sysDictDao.deleteByGroup(id);
        this.sysDictGroupDao.deleteById(id);
    }

    @Operation(operationId = "dict_listDict", summary = "列出某个字典分类（groupCode）下的全部字典项")
    @SaCheckPermission("system:dict:view")
    @GetMapping("")
    @ResponseBody
    public List<SysDict> listDict(@RequestParam("groupCode") String groupCode) {
        return this.sysDictDao.findByGroup(groupCode);
    }

    @Operation(
            operationId = "dict_addDict",
            summary = "在指定字典分类下新增一条字典项",
            description = "请求体需包含 groupCode、code、name 等字段。")
    @SaCheckPermission("system:dict:edit")
    @PostMapping("")
    @ResponseBody
    public SysDict addDict(@RequestBody SysDict sysDict) {
        this.sysDictDao.insert(sysDict);
        return sysDict;
    }

    @SaCheckPermission("system:dict:edit")
    @PutMapping("{id}")
    @ResponseBody
    public SysDict editDict(@PathVariable("id") String id, @RequestBody SysDict sysDict) {
        sysDict.setId(id);
        this.sysDictDao.updateSelective(sysDict);
        return sysDict;
    }

    @SaCheckPermission("system:dict:delete")
    @DeleteMapping("{id}")
    @ResponseBody
    public void deleteDict(@PathVariable("id") String id) {
        this.sysDictDao.deleteById(id);
    }
}
