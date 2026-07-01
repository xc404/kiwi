package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.project.bpm.dto.BpmTemplatePackDetailDto;
import com.kiwi.project.bpm.dto.InstallTemplatePackInput;
import com.kiwi.project.bpm.dto.InstallTemplatePackResult;
import com.kiwi.project.bpm.dto.PublishTemplatePackInput;
import com.kiwi.project.bpm.dto.TemplatePackZipDownload;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.model.BpmTemplatePack;
import com.kiwi.project.bpm.model.BpmTemplateProcess;
import com.kiwi.project.bpm.service.BpmTemplatePackBundleService;
import com.kiwi.project.bpm.service.BpmTemplatePackInstallService;
import com.kiwi.project.bpm.service.BpmTemplatePackPublishService;
import com.kiwi.project.bpm.service.BpmTemplatePackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@SaCheckLogin
@RestController
@RequestMapping("bpm/market")
@RequiredArgsConstructor
@Tag(name = "BPM 模板市场", description = "流程模板包浏览、发布、安装与导入导出")
public class BpmTemplatePackCtl extends BaseCtl {

    private final BpmTemplatePackService packService;
    private final BpmTemplatePackPublishService publishService;
    private final BpmTemplatePackInstallService installService;
    private final BpmTemplatePackBundleService bundleService;

    @Operation(operationId = "bpmMarket_page", summary = "分页查询模板包")
    @GetMapping("")
    @ResponseBody
    public Page<BpmTemplatePack> page(
            BpmTemplatePackService.PackQueryInput query,
            Pageable pageable) {
        return packService.page(query, pageable, getCurrentUserId());
    }

    @Operation(
            operationId = "bpmMarket_aiPage",
            summary = "分页查询模板包（AI/MCP）",
            description = "page 从 0 开始，size 默认 20、最大 100。")
    @GetMapping("/search/ai-page")
    @ResponseBody
    public Page<BpmTemplatePack> aiPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        BpmTemplatePackService.PackQueryInput q = new BpmTemplatePackService.PackQueryInput();
        q.setKeyword(keyword);
        q.setCategory(category);
        q.setTag(tag);
        q.setKind(kind);
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return packService.page(q, PageRequest.of(p, s), getCurrentUserId());
    }

    @Operation(operationId = "bpmMarket_get", summary = "获取模板包详情")
    @GetMapping("{packId}")
    @ResponseBody
    public BpmTemplatePackDetailDto get(@PathVariable String packId) {
        return packService.getDetail(packId, getCurrentUserId());
    }

    @Operation(operationId = "bpmMarket_listProcesses", summary = "列出模板包内流程")
    @GetMapping("{packId}/processes")
    @ResponseBody
    public List<BpmTemplateProcess> listProcesses(@PathVariable String packId) {
        return packService.listProcesses(packId, getCurrentUserId());
    }

    @Operation(operationId = "bpmMarket_getProcess", summary = "获取模板包内单条流程 BPMN")
    @GetMapping("{packId}/processes/{processKey}")
    @ResponseBody
    public BpmTemplateProcess getProcess(
            @PathVariable String packId,
            @PathVariable String processKey) {
        return packService.getProcess(packId, processKey, getCurrentUserId());
    }

    @Operation(operationId = "bpmMarket_publishProject", summary = "从项目导出为模板包")
    @PostMapping("publish/project/{projectId}")
    @ResponseBody
    public BpmTemplatePack publishProject(
            @PathVariable String projectId,
            @RequestBody PublishTemplatePackInput body) {
        return publishService.publishProject(projectId, body, getCurrentUserId());
    }

    @Operation(operationId = "bpmMarket_publishProcess", summary = "从单流程发布为模板包")
    @PostMapping("publish/process/{processId}")
    @ResponseBody
    public BpmTemplatePack publishProcess(
            @PathVariable String processId,
            @RequestBody PublishTemplatePackInput body) {
        return publishService.publishProcess(processId, body, getCurrentUserId());
    }

    @Operation(operationId = "bpmMarket_installPack", summary = "安装模板包（新建项目）")
    @PostMapping("{packId}/install")
    @ResponseBody
    public InstallTemplatePackResult installPack(
            @PathVariable String packId,
            @RequestBody(required = false) InstallTemplatePackInput body) {
        return installService.installPack(packId, body, getCurrentUserId());
    }

    @Operation(operationId = "bpmMarket_installPackInto", summary = "安装模板包到已有项目")
    @PostMapping("{packId}/install-into")
    @ResponseBody
    public InstallTemplatePackResult installPackInto(
            @PathVariable String packId,
            @RequestBody InstallTemplatePackInput body) {
        return installService.installPackInto(packId, body, getCurrentUserId());
    }

    @Operation(operationId = "bpmMarket_installProcess", summary = "安装模板包内单条流程到项目")
    @PostMapping("{packId}/install-process/{processKey}")
    @ResponseBody
    public BpmProcess installProcess(
            @PathVariable String packId,
            @PathVariable String processKey,
            @RequestParam String targetProjectId) {
        return installService.installProcess(packId, processKey, targetProjectId, getCurrentUserId());
    }

    @Operation(operationId = "bpmMarket_exportPack", summary = "导出模板包为 zip")
    @GetMapping("{packId}/export")
    public ResponseEntity<byte[]> exportPack(@PathVariable String packId) {
        TemplatePackZipDownload file = bundleService.exportPackDownload(packId, getCurrentUserId());
        return zipResponse(file);
    }

    @Operation(operationId = "bpmMarket_exportProject", summary = "从项目直接导出模板包 zip")
    @GetMapping("export/project/{projectId}")
    public ResponseEntity<byte[]> exportProject(@PathVariable String projectId) {
        TemplatePackZipDownload file = bundleService.exportProjectDownload(projectId, getCurrentUserId());
        return zipResponse(file);
    }

    private ResponseEntity<byte[]> zipResponse(TemplatePackZipDownload file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file.body());
    }

    @Operation(operationId = "bpmMarket_importPack", summary = "导入模板包 zip 到市场")
    @PostMapping(value = "import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public BpmTemplatePack importPack(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) String visibility) {
        PublishTemplatePackInput meta = new PublishTemplatePackInput();
        meta.setName(name);
        meta.setSummary(summary);
        if (visibility != null) {
            meta.setVisibility(BpmTemplatePack.Visibility.valueOf(visibility));
        }
        return bundleService.importPack(file, meta, getCurrentUserId());
    }

    @Operation(operationId = "bpmMarket_importAndInstall", summary = "导入模板包 zip 并安装为新项目")
    @PostMapping(value = "import-and-install", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public InstallTemplatePackResult importAndInstall(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String projectName) {
        InstallTemplatePackInput install = new InstallTemplatePackInput();
        install.setProjectName(projectName);
        return bundleService.importAndInstall(file, install, getCurrentUserId());
    }
}
