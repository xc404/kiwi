package com.kiwi.project.system.ctl.common;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.springframework.ai.tool.annotation.Tool;
import com.kiwi.common.tree.Node;
import com.kiwi.project.system.spi.Refreshable;
import com.kiwi.project.system.service.DictService;
import com.kiwi.project.system.spi.Dict;
import com.kiwi.project.system.spi.DictGroup;
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
public class CommonCtl
{

    private final DictService dictService;
    private final List<Refreshable> refreshableList;

    @Tool(name = "common_dictGroups", description = "获取全部字典分组列表（会先刷新缓存）。")
    @GetMapping("/common/dict/groups")
    public List<DictGroup> getDictGroups() {
        this.refreshCache();
        return dictService.getDictGroups();
    }


    @GetMapping("/common/dict/{groupCode}")
    public Page<Dict> getDictList(@PathVariable("groupCode") String groupCode, String pattern, Pageable pageable) {
        return dictService.getDictList(groupCode, pattern, pageable);
    }

    @Tool(
            name = "common_dictListPage",
            description = "分页查询某字典分组下的字典项。pattern 可选模糊匹配；page 从 0 开始，size 默认 20、最大 100。")
    public Page<Dict> aiDictListPage(String groupCode, String pattern, Integer page, Integer size) {
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return getDictList(groupCode, pattern, PageRequest.of(p, s));
    }


    @GetMapping("/common/tree/{groupCode}/{parentId}")
    public  List<Node> getTree(@PathVariable("groupCode") String groupCode,
                               @PathVariable(value = "parentId", required = false) String parentId,

                               @RequestParam(defaultValue = "false", name = "loadAll") boolean loadAll,
                               @RequestParam Map<String, Object> extraParams
    ) {
        return dictService.getTree(groupCode, parentId, loadAll, extraParams);
    }

    @Tool(
            name = "common_dictTree",
            description = "获取字典树形数据。loadAll 为 true 时加载整树；extra 参数一般传空对象 {}。")
    public List<Node> aiDictTree(String groupCode, String parentId, boolean loadAll, Map<String, Object> extraParams) {
        Map<String, Object> extra = extraParams != null ? extraParams : new HashMap<>();
        return getTree(groupCode, parentId, loadAll, extra);
    }

    @Tool(name = "common_refreshCache", description = "刷新系统缓存（需 admin 角色）。")
    @PostMapping("/system/cache/refresh")
    @SaCheckRole("admin")
    public void refreshCache() {
        refreshableList.forEach(c -> {
            c.refresh();
        });
    }


}
