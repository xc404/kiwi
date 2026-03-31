package com.kiwi.project.system.ctl.common;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.kiwi.common.tree.Node;
import com.kiwi.project.system.spi.Refreshable;
import com.kiwi.project.system.service.DictService;
import com.kiwi.project.system.spi.Dict;
import com.kiwi.project.system.spi.DictGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
public class CommonCtl
{

    private final DictService dictService;
    private final List<Refreshable> refreshableList;

    @GetMapping("/common/dict/groups")
    public List<DictGroup> getDictGroups() {
        this.refreshCache();
        return dictService.getDictGroups();
    }


    @GetMapping("/common/dict/{groupCode}")
    public Page<Dict> getDictList(@PathVariable("groupCode") String groupCode, String pattern, Pageable pageable) {
        return dictService.getDictList(groupCode, pattern, pageable);
    }


    @GetMapping("/common/tree/{groupCode}/{parentId}")
    public  List<Node> getTree(@PathVariable("groupCode") String groupCode,
                               @PathVariable(value = "parentId", required = false) String parentId,

                               @RequestParam(defaultValue = "false", name = "loadAll") boolean loadAll,
                               @RequestParam Map<String, Object> extraParams
    ) {
        return dictService.getTree(groupCode, parentId, loadAll, extraParams);
    }

    @PostMapping("/system/cache/refresh")
    @SaCheckRole("admin")
    public void refreshCache() {
        refreshableList.forEach(c -> {
            c.refresh();
        });
    }


}
