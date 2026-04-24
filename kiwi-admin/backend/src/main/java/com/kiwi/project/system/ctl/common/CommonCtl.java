package com.kiwi.project.system.ctl.common;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.kiwi.common.tree.Node;
import com.kiwi.project.system.spi.Refreshable;
import com.kiwi.project.system.service.DictService;
import com.kiwi.project.system.spi.Dict;
import com.kiwi.project.system.spi.DictGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@Tag(name = "公共字典与缓存", description = "字典分组、分页、树与缓存刷新")
public class CommonCtl {

    private final DictService dictService;
    private final List<Refreshable> refreshableList;

    @Operation(operationId = "common_dictGroups", summary = "获取全部字典分组列表（会先刷新缓存）")
    @GetMapping("/common/dict/groups")
    public List<DictGroup> getDictGroups() {
        this.refreshCache();
        return dictService.getDictGroups();
    }

    @GetMapping("/common/dict/{groupCode}")
    public Page<Dict> getDictList(@PathVariable("groupCode") String groupCode, String pattern, Pageable pageable) {
        return dictService.getDictList(groupCode, pattern, pageable);
    }

    @Operation(
            operationId = "common_dictListPage",
            summary = "分页查询某字典分组下的字典项",
            description = "pattern 可选模糊匹配；page 从 0 开始，size 默认 20、最大 100。")
    @GetMapping("/common/dict/{groupCode}/ai-page")
    public Page<Dict> aiDictListPage(
            @PathVariable("groupCode") String groupCode,
            @RequestParam(required = false) String pattern,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return getDictList(groupCode, pattern, PageRequest.of(p, s));
    }

    @GetMapping("/common/tree/{groupCode}/{parentId}")
    public List<Node> getTree(@PathVariable("groupCode") String groupCode,
                              @PathVariable(value = "parentId", required = false) String parentId,
                              @RequestParam(defaultValue = "false", name = "loadAll") boolean loadAll,
                              @RequestParam Map<String, Object> extraParams) {
        return dictService.getTree(groupCode, parentId, loadAll, extraParams);
    }

    @Operation(
            operationId = "common_dictTree",
            summary = "获取字典树形数据",
            description = "loadAll 为 true 时加载整树；extra 参数一般传空对象 {}。")
    @GetMapping("/common/tree/{groupCode}/{parentId}/ai")
    public List<Node> aiDictTree(
            @PathVariable("groupCode") String groupCode,
            @PathVariable(value = "parentId", required = false) String parentId,
            @RequestParam(defaultValue = "false", name = "loadAll") boolean loadAll,
            @RequestParam(required = false) Map<String, Object> extraParams) {
        Map<String, Object> extra = extraParams != null ? extraParams : new HashMap<>();
        return getTree(groupCode, parentId, loadAll, extra);
    }

    @Operation(operationId = "common_refreshCache", summary = "刷新系统缓存（需 admin 角色）")
    @PostMapping("/system/cache/refresh")
    @SaCheckRole("admin")
    public void refreshCache() {
        refreshableList.forEach(c -> {
            c.refresh();
        });
    }
}
