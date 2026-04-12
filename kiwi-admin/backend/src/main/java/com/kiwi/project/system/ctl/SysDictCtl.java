package com.kiwi.project.system.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import org.springframework.ai.tool.annotation.Tool;
import com.kiwi.project.system.dao.SysDictDao;
import com.kiwi.project.system.dao.SysDictGroupDao;
import com.kiwi.project.system.entity.SysDict;
import com.kiwi.project.system.entity.SysDictGroup;
import io.swagger.v3.oas.annotations.Operation;
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
public class SysDictCtl
{
    private final SysDictGroupDao sysDictGroupDao;

    private final SysDictDao sysDictDao;


    public static class SearchInput
    {
        @QueryField(value = "groupCode", condition = QueryField.Type.LIKE)
        public String groupCode;
        @QueryField(value = "groupName", condition = QueryField.Type.LIKE)
        public String groupName;
    }

    @GetMapping("/group")
    @SaCheckPermission("system:dict:view")
    @Operation(description = "字典组查看")
    @ResponseBody
    public Page<SysDictGroup> listGroup(SearchInput searchInput, Pageable pageable) {
        return this.sysDictGroupDao.findBy(QueryParams.of(searchInput), pageable);
    }

    /**
     * 供 AI / MCP 调用：分页查询字典分类（参数较 HTTP 接口更扁平，便于模型填参）。
     */
    @Tool(
            name = "dict_aiSearchDictGroups",
            description = "分页查询字典分类列表。groupCode、groupName 支持模糊匹配；page 从 0 开始，size 默认 20、最大 100。")
    @SaCheckPermission("system:dict:view")
    public Page<SysDictGroup> aiSearchDictGroups(String groupCode, String groupName, Integer page, Integer size) {
        SearchInput searchInput = new SearchInput();
        searchInput.groupCode = groupCode;
        searchInput.groupName = groupName;
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return listGroup(searchInput, PageRequest.of(p, s));
    }

    @Tool(
            name = "dict_addDictGroup",
            description = "新增字典分类（字典组）。请求体字段与系统管理界面一致，如 groupCode、groupName、status、remark。")
    @SaCheckPermission("system:dict:edit")
    @PostMapping("/group")
    @Operation(description = "字典组添加")
    @ResponseBody
    public SysDictGroup addDictGroup(@RequestBody SysDictGroup sysDict) {
        this.sysDictGroupDao.insert(sysDict);
        return sysDict;
    }

    @SaCheckPermission("system:dict:edit")
    @PutMapping("/group/{id}")
    @Operation(description = "字典组添加")
    @ResponseBody
    public SysDictGroup updateDictGroup(@PathVariable("id") String id, @RequestBody SysDictGroup sysDict) {
        sysDict.setGroupCode(id);
        this.sysDictGroupDao.updateSelective(sysDict);
        return sysDict;
    }

    @SaCheckPermission("system:dict:delete")
    @DeleteMapping("/group/{id}")
    @Operation(description = "字典组删除")
    @ResponseBody
    public void deleteDictGroup(@PathVariable("id") String id) {
        this.sysDictDao.deleteByGroup(id);
        this.sysDictGroupDao.deleteById(id);
    }

    @Tool(name = "dict_listDict", description = "列出某个字典分类（groupCode）下的全部字典项。")
    @SaCheckPermission("system:dict:view")
    @GetMapping("")
    @Operation(description = "字典查看")
    @ResponseBody
    public List<SysDict> listDict(@RequestParam("groupCode") String groupCode) {
        return this.sysDictDao.findByGroup(groupCode);
    }

    @Tool(
            name = "dict_addDict",
            description = "在指定字典分类下新增一条字典项。请求体需包含 groupCode、code、name 等字段。")
    @SaCheckPermission("system:dict:edit")
    @PostMapping("")
    @Operation(description = "字典编辑")
    @ResponseBody
    public SysDict addDict(@RequestBody SysDict sysDict) {
        this.sysDictDao.insert(sysDict);
        return sysDict;
    }

    @SaCheckPermission("system:dict:edit")
    @PutMapping("{id}")
    @Operation(description = "字典修改")
    @ResponseBody
    public SysDict editDict(@PathVariable("id") String id, @RequestBody SysDict sysDict) {
        sysDict.setId(id);
        this.sysDictDao.updateSelective(sysDict);
        return sysDict;
    }

    @SaCheckPermission("system:dict:delete")
    @DeleteMapping("{id}")
    @Operation(description = "字典删除")
    @ResponseBody
    public void deleteDict(@PathVariable("id") String id) {
        this.sysDictDao.deleteById(id);
    }
}
