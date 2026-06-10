package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.project.bpm.dto.BpmProjectEnvVarDto;
import com.kiwi.project.bpm.model.BpmProjectEnvVar;
import com.kiwi.project.bpm.service.BpmOwnershipAccessService;
import com.kiwi.project.bpm.service.BpmProjectEnvService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@SaCheckLogin
@RestController
@RequestMapping("bpm/project/{projectId}/env")
@RequiredArgsConstructor
@Tag(name = "BPM 项目环境变量", description = "按项目配置环境变量，启动流程时注入")
public class BpmProjectEnvCtl extends BaseCtl {

    private final BpmProjectEnvService bpmProjectEnvService;
    private final BpmOwnershipAccessService bpmOwnershipAccessService;

    @Operation(operationId = "bpmProjEnv_list", summary = "列出项目环境变量")
    @GetMapping
    @ResponseBody
    public Page<BpmProjectEnvVarDto> list(@PathVariable String projectId, Pageable pageable) {
        bpmOwnershipAccessService.assertOwnsProject(getCurrentUserId(), projectId);
        return bpmProjectEnvService.pageByProject(projectId, pageable);
    }

    @Operation(operationId = "bpmProjEnv_get", summary = "按 id 获取项目环境变量")
    @GetMapping("{id}")
    @ResponseBody
    public BpmProjectEnvVarDto get(@PathVariable String projectId, @PathVariable String id) {
        bpmOwnershipAccessService.assertOwnsProject(getCurrentUserId(), projectId);
        return requireInProject(projectId, bpmProjectEnvService.getDto(id));
    }

    @Operation(operationId = "bpmProjEnv_add", summary = "新增项目环境变量")
    @PostMapping
    @ResponseBody
    public BpmProjectEnvVarDto add(@PathVariable String projectId, @RequestBody BpmProjectEnvVar body) {
        bpmOwnershipAccessService.assertOwnsProject(getCurrentUserId(), projectId);
        body.setProjectId(projectId);
        return bpmProjectEnvService.create(body);
    }

    @Operation(operationId = "bpmProjEnv_update", summary = "按 id 更新项目环境变量")
    @PutMapping("{id}")
    @ResponseBody
    public BpmProjectEnvVarDto update(
            @PathVariable String projectId, @PathVariable String id, @RequestBody BpmProjectEnvVar body) {
        bpmOwnershipAccessService.assertOwnsProject(getCurrentUserId(), projectId);
        requireInProject(projectId, bpmProjectEnvService.getDto(id));
        return bpmProjectEnvService.update(id, body);
    }

    @Operation(operationId = "bpmProjEnv_delete", summary = "按 id 删除项目环境变量")
    @DeleteMapping("{id}")
    @ResponseBody
    public void delete(@PathVariable String projectId, @PathVariable String id) {
        bpmOwnershipAccessService.assertOwnsProject(getCurrentUserId(), projectId);
        requireInProject(projectId, bpmProjectEnvService.getDto(id));
        bpmProjectEnvService.delete(id);
    }

    private static BpmProjectEnvVarDto requireInProject(String projectId, BpmProjectEnvVarDto dto) {
        if (!projectId.equals(dto.getProjectId())) {
            throw new IllegalArgumentException("环境变量不属于该项目");
        }
        return dto;
    }
}
