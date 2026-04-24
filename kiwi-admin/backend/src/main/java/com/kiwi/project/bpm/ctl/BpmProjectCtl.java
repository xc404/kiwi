package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.dao.BpmProjectDao;
import com.kiwi.project.bpm.model.BpmProject;
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

@SaCheckLogin
@RestController
@RequestMapping("bpm/project")
@RequiredArgsConstructor
@Tag(name = "BPM 项目", description = "项目/文件夹 CRUD")
public class BpmProjectCtl extends BaseCtl {

    private final BpmProjectDao bpmProjectDao;
    private final BpmProcessDefinitionDao bpmProcessDefinitionDao;

    @GetMapping("")
    @ResponseBody
    public Page<BpmProject> getProjects(Pageable pageable) {
        return bpmProjectDao.findAll(pageable);
    }

    @Operation(
            operationId = "bpmProj_page",
            summary = "分页查询 BPM 项目/文件夹",
            description = "page 从 0 开始，size 默认 20、最大 100。")
    @GetMapping("/search/ai-page")
    @ResponseBody
    public Page<BpmProject> aiPageProjects(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return getProjects(PageRequest.of(p, s));
    }

    @Operation(operationId = "bpmProj_get", summary = "按 id 获取 BPM 项目")
    @GetMapping("/{id}")
    @ResponseBody
    public BpmProject get(@PathVariable String id) {
        return bpmProjectDao.findById(id).orElse(null);
    }

    @Operation(operationId = "bpmProj_add", summary = "新增 BPM 项目/文件夹")
    @PostMapping("")
    @ResponseBody
    public BpmProject add(@RequestBody BpmProject folder) {
        return bpmProjectDao.save(folder);
    }

    @Operation(operationId = "bpmProj_update", summary = "按 id 更新 BPM 项目")
    @PutMapping("{id}")
    @ResponseBody
    public BpmProject update(@PathVariable String id, @RequestBody BpmProject bpmProject) {
        bpmProjectDao.updateSelective(bpmProject);
        return bpmProject;
    }

    @Operation(operationId = "bpmProj_delete", summary = "按 id 删除 BPM 项目")
    @DeleteMapping("{id}")
    @ResponseBody
    public void delete(@PathVariable String id) {
        bpmProjectDao.deleteById(id);
    }
}
